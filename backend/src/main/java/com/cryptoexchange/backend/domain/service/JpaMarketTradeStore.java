package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.model.MarketTrade;
import com.cryptoexchange.backend.domain.repository.MarketTradeRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JPA-based implementation of MarketTradeStore.
 */
@Component
public class JpaMarketTradeStore implements MarketTradeStore {
    
    private final MarketTradeRepository repository;
    
    public JpaMarketTradeStore(MarketTradeRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public void saveAll(List<MarketTrade> trades) {
        if (trades != null && !trades.isEmpty()) {
            repository.saveAll(trades);
        }
    }
    
    @Override
    public List<MarketTrade> findRecent(String marketSymbol, int limit) {
        List<MarketTrade> all = repository.findTop50ByMarketSymbolOrderByTsDesc(marketSymbol);
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(0, limit);
    }
}
