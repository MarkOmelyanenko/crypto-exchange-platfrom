package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.NotFoundException;
import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.model.Market;
import com.cryptoexchange.backend.domain.repository.MarketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

    private final MarketRepository marketRepository;
    private final AssetService assetService;

    public MarketService(final MarketRepository marketRepository, final AssetService assetService) {
        this.marketRepository = marketRepository;
        this.assetService = assetService;
    }

    public Market createMarket(UUID baseAssetId, UUID quoteAssetId, String symbol) {
        if (baseAssetId.equals(quoteAssetId)) {
            throw new IllegalArgumentException("Base asset and quote asset cannot be the same");
        }
        if (marketRepository.findBySymbol(symbol).isPresent()) {
            throw new IllegalArgumentException("Market with symbol " + symbol + " already exists");
        }
        
        Asset baseAsset = assetService.getAsset(baseAssetId);
        Asset quoteAsset = assetService.getAsset(quoteAssetId);
        
        Market market = new Market(baseAsset, quoteAsset, symbol);
        Market saved = marketRepository.save(market);
        log.info("Created market: {} (base: {}, quote: {})", symbol, baseAsset.getSymbol(), quoteAsset.getSymbol());
        return saved;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "markets", key = "'all'")
    public List<Market> listActiveMarkets() {
        return marketRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Market getMarket(UUID marketId) {
        return marketRepository.findById(marketId)
            .orElseThrow(() -> new NotFoundException("Market not found with id: " + marketId));
    }

    @Transactional(readOnly = true)
    public Market getMarketBySymbol(String symbol) {
        return marketRepository.findBySymbol(symbol)
            .orElseThrow(() -> new NotFoundException("Market not found with symbol: " + symbol));
    }
}
