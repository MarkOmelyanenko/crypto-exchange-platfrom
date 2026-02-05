package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.model.MarketTick;
import com.cryptoexchange.backend.domain.repository.MarketTickRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * JPA-based implementation of MarketTickStore.
 */
@Component
public class JpaMarketTickStore implements MarketTickStore {
    
    private final MarketTickRepository repository;
    
    public JpaMarketTickStore(MarketTickRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public void saveAll(List<MarketTick> ticks) {
        if (ticks != null && !ticks.isEmpty()) {
            repository.saveAll(ticks);
        }
    }
    
    @Override
    public MarketTick findLatest(String marketSymbol) {
        return repository.findFirstByMarketSymbolOrderByTsDesc(marketSymbol)
            .orElse(null);
    }
    
    @Override
    public List<MarketTick> findRange(String marketSymbol, OffsetDateTime from, 
                                     OffsetDateTime to, int page, int size) {
        if (from == null) {
            from = OffsetDateTime.now().minusDays(1);
        }
        if (to == null) {
            to = OffsetDateTime.now();
        }
        return repository.findByMarketSymbolAndTsBetween(
            marketSymbol, from, to, PageRequest.of(page, size)
        ).getContent();
    }
}
