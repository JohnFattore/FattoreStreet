package com.fattorestreet.sec_api.listing;

import org.springframework.stereotype.Service;
import com.fattorestreet.sec_api.model.Asset;
import com.fattorestreet.sec_api.repository.AssetRepository;

@Service
public class AssetService {

    private final AssetRepository assetRepository;

    // constructor
    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public Asset saveAsset(Asset asset) {
        return assetRepository.save(asset);
    }

    public Asset createOrUpdateAsset(Asset asset) {
        return assetRepository.findByCik(asset.getCik())
                .map(existing -> {
                    // Keep fund classification sticky once true.
                    boolean existingIsFund = Boolean.TRUE.equals(existing.getIsFund());
                    boolean incomingIsFund = Boolean.TRUE.equals(asset.getIsFund());
                    existing.setIsFund(existingIsFund || incomingIsFund);
                    return assetRepository.save(existing);
                })
                .orElseGet(() -> {
                    return assetRepository.save(asset);
                });
    }
}