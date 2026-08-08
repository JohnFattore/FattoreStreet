package com.fattorestreet.sec_api.corporateaction.support;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fattorestreet.sec_api.model.FilingExtraction;
import com.fattorestreet.sec_api.repository.FilingExtractionRepository;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilingExtractionStoreTest {

    private static final long CIK = 320193L;
    private static final String ACCESSION = "0000320193-20-000062";

    @Mock
    private FilingExtractionRepository repository;

    private FilingExtractionStore store;

    @BeforeEach
    void setUp() {
        store = new FilingExtractionStore(repository, new ObjectMapper());
        lenient().when(repository.save(any(FilingExtraction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // @spec FILING-018, FILING-022
    @Test
    void dividendSectionRoundTripsThroughPersistedRow() {
        FilingExtractionStore.DividendExtraction section = new FilingExtractionStore.DividendExtraction(
                new FilingExtractionStore.DatedScore(LocalDate.of(2020, 8, 10), 155),
                List.of(new FilingExtractionStore.DatedScore(LocalDate.of(2020, 8, 7), 135)),
                List.of(new DividendDeclarationTupleExtractor.DividendDeclaration(
                        0.82, LocalDate.of(2020, 7, 30), LocalDate.of(2020, 8, 10),
                        LocalDate.of(2020, 8, 13), null, LocalDate.of(2020, 7, 31), ACCESSION, 95)));

        FilingExtraction row = store.saveDividendSection(
                CIK, ACCESSION, LocalDate.of(2020, 7, 31), "8-K", null, section);

        assertEquals(FilingExtractionStore.DIVIDEND_EXTRACTOR_VERSION, row.getDividendExtractorVersion());
        Optional<FilingExtractionStore.DividendExtraction> loaded = store.dividendSection(row);
        assertTrue(loaded.isPresent());
        assertEquals(LocalDate.of(2020, 8, 10), loaded.get().bestRecordDate().date());
        assertEquals(155, loaded.get().bestRecordDate().score());
        assertEquals(1, loaded.get().exDateCandidates().size());
        assertEquals(LocalDate.of(2020, 8, 7), loaded.get().exDateCandidates().get(0).date());
        assertEquals(1, loaded.get().declarations().size());
        DividendDeclarationTupleExtractor.DividendDeclaration declaration = loaded.get().declarations().get(0);
        assertEquals(0.82, declaration.amountPerShare(), 1e-9);
        assertEquals(LocalDate.of(2020, 7, 30), declaration.declarationDate());
        assertEquals(LocalDate.of(2020, 8, 10), declaration.recordDate());
        assertEquals(LocalDate.of(2020, 8, 13), declaration.payableDate());
        assertNull(declaration.exDate());
        assertEquals(LocalDate.of(2020, 7, 31), declaration.filingDate());
        assertEquals(ACCESSION, declaration.accessionNumber());
        assertEquals(95, declaration.confidenceScore());
    }

    // @spec FILING-022
    @Test
    void emptyDividendSectionRoundTripsAsPresentButEmpty() {
        // "Scanned and found nothing" must persist as a present section so the accession is
        // never re-fetched, distinct from "never scanned".
        FilingExtraction row = store.saveDividendSection(
                CIK, ACCESSION, LocalDate.of(2020, 7, 31), "10-Q", null,
                new FilingExtractionStore.DividendExtraction(null, List.of(), List.of()));

        Optional<FilingExtractionStore.DividendExtraction> loaded = store.dividendSection(row);
        assertTrue(loaded.isPresent());
        assertNull(loaded.get().bestRecordDate());
        assertTrue(loaded.get().exDateCandidates().isEmpty());
        assertTrue(loaded.get().declarations().isEmpty());
    }

    // @spec FILING-018
    @Test
    void splitSectionRoundTripsAndSharesRowWithDividendSection() {
        FilingExtraction row = store.saveDividendSection(
                CIK, ACCESSION, LocalDate.of(2020, 7, 31), "8-K", null,
                new FilingExtractionStore.DividendExtraction(null, List.of(), List.of()));
        row = store.saveSplitSection(
                CIK, ACCESSION, LocalDate.of(2020, 7, 31), "8-K", row,
                new FilingExtractionStore.SplitExtraction(
                        new FilingExtractionStore.DatedScore(LocalDate.of(2020, 8, 31), 165)));

        assertTrue(store.dividendSection(row).isPresent());
        Optional<FilingExtractionStore.SplitExtraction> split = store.splitSection(row);
        assertTrue(split.isPresent());
        assertEquals(LocalDate.of(2020, 8, 31), split.get().bestSplitDate().date());
        assertEquals(165, split.get().bestSplitDate().score());
        verify(repository, org.mockito.Mockito.times(2)).save(any(FilingExtraction.class));
    }

    // @spec FILING-019
    @Test
    void versionMismatchReturnsEmptySection() {
        FilingExtraction row = new FilingExtraction();
        row.setAccessionNumber(ACCESSION);
        row.setDividendExtractorVersion(FilingExtractionStore.DIVIDEND_EXTRACTOR_VERSION - 1);
        row.setSplitExtractorVersion(FilingExtractionStore.SPLIT_EXTRACTOR_VERSION - 1);

        assertTrue(store.dividendSection(row).isEmpty());
        assertTrue(store.splitSection(row).isEmpty());
        assertTrue(store.dividendSection(null).isEmpty());
        assertTrue(store.splitSection(null).isEmpty());
    }

    // @spec FILING-018
    @Test
    void loadByCikKeysRowsByAccession() {
        FilingExtraction row = new FilingExtraction();
        row.setCik(CIK);
        row.setAccessionNumber(ACCESSION);
        when(repository.findByCik(CIK)).thenReturn(List.of(row));

        assertSame(row, store.loadByCik(CIK).get(ACCESSION));
    }
}
