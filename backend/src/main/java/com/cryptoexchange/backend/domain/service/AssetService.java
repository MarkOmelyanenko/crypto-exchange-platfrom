package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.repository.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AssetService {

    private final AssetRepository assetRepository;

    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public Asset createAsset(String symbol, String name, Integer scale) {
        if (assetRepository.findBySymbol(symbol).isPresent()) {
            throw new IllegalArgumentException("Asset with symbol " + symbol + " already exists");
        }
        Asset asset = new Asset(symbol, name, scale);
        return assetRepository.save(asset);
    }

    @Transactional(readOnly = true)
    public List<Asset> listAssets() {
        return assetRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Asset getAsset(UUID assetId) {
        return assetRepository.findById(assetId)
            .orElseThrow(() -> new NotFoundException("Asset not found with id: " + assetId));
    }

    @Transactional(readOnly = true)
    public Asset getAssetBySymbol(String symbol) {
        return assetRepository.findBySymbol(symbol)
            .orElseThrow(() -> new NotFoundException("Asset not found with symbol: " + symbol));
    }
}
