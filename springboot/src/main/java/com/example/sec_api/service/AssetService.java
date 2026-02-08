package com.example.sec_api.service;

import org.springframework.stereotype.Service;
import com.example.sec_api.model.Asset;
import com.example.sec_api.repository.AssetRepository;

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
                    // update fields if needed
                    existing.setIsFund(asset.getIsFund());
                    return assetRepository.save(existing);
                })
                .orElseGet(() -> {
                    return assetRepository.save(asset);
                });
    }
}