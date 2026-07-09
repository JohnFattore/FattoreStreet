package com.fattorestreet.sec_api.corporateaction.support;

import com.fattorestreet.sec_api.corporateaction.support.EtfActionPersister.PersistResult;
import com.fattorestreet.sec_api.corporateaction.support.EtfDateExtractor.EtfDateSignals;
import com.fattorestreet.sec_api.model.CorporateAction;
import com.fattorestreet.sec_api.model.CorporateAction.ActionType;
import com.fattorestreet.sec_api.model.CorporateAction.SourceType;
import com.fattorestreet.sec_api.model.Listing;
import com.fattorestreet.sec_api.repository.CorporateActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EtfActionPersisterTest {

    private static final LocalDate EX_DATE = LocalDate.of(2025, 3, 20);

    @Mock
    private CorporateActionRepository corporateActionRepository;

    @InjectMocks
    private EtfActionPersister persister;

    private Listing listing;
    private EtfDateSignals signals;

    @BeforeEach
    void setUp() {
        listing = new Listing();
        listing.setTicker("VOO");
        listing.setSecSeriesId("S000002277");
        listing.setSecClassContractId("C000005780");
        signals = new EtfDateSignals(
                EX_DATE,
                LocalDate.of(2025, 3, 21),
                LocalDate.of(2025, 3, 25),
                85,
                "record+1bd",
                "derived",
                "explicit",
                "explicit");
    }

    @Test
    void savesNewDividendWithAllFields() {
        when(corporateActionRepository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO", ActionType.DIVIDEND, EX_DATE, 1.7181, SourceType.SEC_ETF_FILING))
                .thenReturn(Optional.empty());

        PersistResult result = persister.persistDividend(
                "VOO", listing, "N-CSR", "acc-123", EX_DATE, 1.7181, signals);

        assertEquals(PersistResult.SAVED, result);
        ArgumentCaptor<CorporateAction> captor = ArgumentCaptor.forClass(CorporateAction.class);
        verify(corporateActionRepository).save(captor.capture());
        CorporateAction saved = captor.getValue();
        assertEquals("VOO", saved.getTicker());
        assertEquals(ActionType.DIVIDEND, saved.getActionType());
        assertEquals(EX_DATE, saved.getEffectiveDate());
        assertEquals(1.7181, saved.getRatio());
        assertEquals(1.7181, saved.getRawDividend());
        assertEquals(1.7181, saved.getAdjustedDividend());
        assertEquals(SourceType.SEC_ETF_FILING, saved.getSourceType());
        assertEquals("N-CSR", saved.getFormType());
        assertEquals("acc-123", saved.getAccessionNumber());
        assertEquals(LocalDate.of(2025, 3, 21), saved.getRecordDate());
        assertEquals(LocalDate.of(2025, 3, 25), saved.getPayDate());
        assertEquals(85.0, saved.getConfidenceScore());
        assertEquals("S000002277", saved.getSecSeriesId());
        assertEquals("C000005780", saved.getSecClassContractId());
    }

    @Test
    void duplicateDividendIsNotSavedAgain() {
        when(corporateActionRepository.findByTickerAndActionTypeAndEffectiveDateAndRatioAndSourceType(
                "VOO", ActionType.DIVIDEND, EX_DATE, 1.7181, SourceType.SEC_ETF_FILING))
                .thenReturn(Optional.of(new CorporateAction()));

        PersistResult result = persister.persistDividend(
                "VOO", listing, "N-CSR", "acc-123", EX_DATE, 1.7181, signals);

        assertEquals(PersistResult.DUPLICATE, result);
        verify(corporateActionRepository, never()).save(any());
    }
}
