package com.fattorestreet.sec_api.service;

import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @InjectMocks
    private AssetService assetService;

    @Test
    void createOrUpdateAsset_existingCik_updatesIsFund() {
        Asset existing = new Asset();
        existing.setId(1L);
        existing.setCik(320193L);
        existing.setIsFund(false);

        Asset incoming = new Asset();
        incoming.setCik(320193L);
        incoming.setIsFund(true);

        when(assetRepository.findByCik(320193L)).thenReturn(Optional.of(existing));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        Asset result = assetService.createOrUpdateAsset(incoming);

        assertEquals(1L, result.getId());
        assertTrue(result.getIsFund());
        verify(assetRepository).save(existing);
    }

    @Test
    void createOrUpdateAsset_existingFund_doesNotDowngradeToEquity() {
        Asset existing = new Asset();
        existing.setId(2L);
        existing.setCik(102909L);
        existing.setIsFund(true);

        Asset incoming = new Asset();
        incoming.setCik(102909L);
        incoming.setIsFund(false);

        when(assetRepository.findByCik(102909L)).thenReturn(Optional.of(existing));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        Asset result = assetService.createOrUpdateAsset(incoming);

        assertEquals(2L, result.getId());
        assertTrue(result.getIsFund());
        verify(assetRepository).save(existing);
    }

    @Test
    void createOrUpdateAsset_newCik_creates() {
        Asset incoming = new Asset();
        incoming.setCik(999999L);
        incoming.setIsFund(false);

        when(assetRepository.findByCik(999999L)).thenReturn(Optional.empty());
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        Asset result = assetService.createOrUpdateAsset(incoming);

        assertEquals(999999L, result.getCik());
        assertFalse(result.getIsFund());
        verify(assetRepository).save(incoming);
    }
}
