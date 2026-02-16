package com.example.sec_api.service;

import com.example.sec_api.model.Asset;
import com.example.sec_api.model.Quarter;
import com.example.sec_api.repository.QuarterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuarterServiceTest {

    @Mock
    private QuarterRepository quarterRepository;

    @InjectMocks
    private QuarterService quarterService;

    private Asset buildAsset(Long cik) {
        Asset a = new Asset();
        a.setId(1L);
        a.setCik(cik);
        return a;
    }

    private Quarter buildQuarter(Asset asset, Integer year, Integer qtr,
                                  LocalDate start, LocalDate end, Long revenue) {
        Quarter q = new Quarter();
        q.setAsset(asset);
        q.setYear(year);
        q.setQuarter(qtr);
        q.setPeriodStart(start);
        q.setPeriodEnd(end);
        q.setRevenues(revenue);
        return q;
    }

    @Test
    void createOrUpdateQuarter_existsByYearAndQuarter_updatesExisting() {
        Asset asset = buildAsset(320193L);
        Quarter existing = buildQuarter(asset, 2024, 4,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31), 100L);
        existing.setId(10L);

        Quarter incoming = buildQuarter(asset, 2024, 4,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31), 200L);

        when(quarterRepository.findByAssetAndYearAndQuarter(asset, 2024, 4))
                .thenReturn(Optional.of(existing));
        when(quarterRepository.save(any(Quarter.class))).thenAnswer(inv -> inv.getArgument(0));

        Quarter result = quarterService.createOrUpdateQuarter(incoming);

        assertEquals(10L, result.getId());
        assertEquals(200L, result.getRevenues());
        verify(quarterRepository).save(existing);
    }

    @Test
    void createOrUpdateQuarter_newQuarter_creates() {
        Asset asset = buildAsset(320193L);
        Quarter incoming = buildQuarter(asset, 2024, 4,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31), 100L);

        when(quarterRepository.findByAssetAndYearAndQuarter(asset, 2024, 4))
                .thenReturn(Optional.empty());
        when(quarterRepository.findByAssetAndPeriodStartAndPeriodEnd(asset,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31)))
                .thenReturn(Optional.empty());
        when(quarterRepository.save(any(Quarter.class))).thenAnswer(inv -> inv.getArgument(0));

        Quarter result = quarterService.createOrUpdateQuarter(incoming);

        assertEquals(100L, result.getRevenues());
        verify(quarterRepository).save(incoming);
    }

    @Test
    void createOrUpdateQuarter_fallsBackToDateLookup() {
        Asset asset = buildAsset(320193L);
        Quarter existing = buildQuarter(asset, 2024, 4,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31), 50L);
        existing.setId(5L);

        Quarter incoming = buildQuarter(asset, 2024, 4,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31), 150L);

        when(quarterRepository.findByAssetAndYearAndQuarter(asset, 2024, 4))
                .thenReturn(Optional.empty());
        when(quarterRepository.findByAssetAndPeriodStartAndPeriodEnd(asset,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31)))
                .thenReturn(Optional.of(existing));
        when(quarterRepository.save(any(Quarter.class))).thenAnswer(inv -> inv.getArgument(0));

        Quarter result = quarterService.createOrUpdateQuarter(incoming);

        assertEquals(5L, result.getId());
        assertEquals(150L, result.getRevenues());
        verify(quarterRepository).save(existing);
    }

    @Test
    void updateAssetQuarters_mergesIncomingDuplicates() {
        Asset asset = buildAsset(320193L);
        LocalDate start = LocalDate.of(2024, 10, 1);
        LocalDate end = LocalDate.of(2024, 12, 31);

        Quarter dup1 = buildQuarter(asset, 2024, 4, start, end, 100L);
        Quarter dup2 = buildQuarter(asset, 2024, 4, start, end, 200L);
        dup2.setNetIncomeLoss(50L);

        when(quarterRepository.findByAsset(asset)).thenReturn(Collections.emptyList());

        quarterService.updateAssetQuarters(asset, List.of(dup1, dup2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Quarter>> captor = ArgumentCaptor.forClass(List.class);
        verify(quarterRepository).saveAll(captor.capture());

        List<Quarter> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(200L, saved.get(0).getRevenues());
        assertEquals(50L, saved.get(0).getNetIncomeLoss());
    }

    @Test
    void updateAssetQuarters_updatesExistingAndAddsNew() {
        Asset asset = buildAsset(320193L);
        LocalDate existStart = LocalDate.of(2024, 7, 1);
        LocalDate existEnd = LocalDate.of(2024, 9, 30);
        LocalDate newStart = LocalDate.of(2024, 10, 1);
        LocalDate newEnd = LocalDate.of(2024, 12, 31);

        Quarter existing = buildQuarter(asset, 2024, 3, existStart, existEnd, 80L);
        existing.setId(7L);

        Quarter incomingUpdate = buildQuarter(asset, 2024, 3, existStart, existEnd, 90L);
        Quarter incomingNew = buildQuarter(asset, 2024, 4, newStart, newEnd, 110L);

        when(quarterRepository.findByAsset(asset)).thenReturn(List.of(existing));

        quarterService.updateAssetQuarters(asset, List.of(incomingUpdate, incomingNew));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Quarter>> captor = ArgumentCaptor.forClass(List.class);
        verify(quarterRepository).saveAll(captor.capture());

        List<Quarter> saved = captor.getValue();
        assertEquals(2, saved.size());

        Quarter updated = saved.stream().filter(q -> q.getId() != null && q.getId() == 7L).findFirst().orElse(null);
        assertNotNull(updated);
        assertEquals(90L, updated.getRevenues());

        Quarter created = saved.stream().filter(q -> q.getId() == null).findFirst().orElse(null);
        assertNotNull(created);
        assertEquals(110L, created.getRevenues());
    }

    @Test
    void batchUpsertQuarters_skipsNullAsset() {
        Quarter withNullAsset = new Quarter();
        withNullAsset.setYear(2024);
        withNullAsset.setQuarter(4);

        Asset asset = buildAsset(320193L);
        Quarter valid = buildQuarter(asset, 2024, 4,
                LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31), 100L);

        when(quarterRepository.findByYearAndQuarter(2024, 4)).thenReturn(Collections.emptyList());

        quarterService.batchUpsertQuarters(2024, 4, List.of(withNullAsset, valid));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Quarter>> captor = ArgumentCaptor.forClass(List.class);
        verify(quarterRepository).saveAll(captor.capture());

        List<Quarter> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(100L, saved.get(0).getRevenues());
    }
}
