package com.cryptoexchange.backend.config;

import com.cryptoexchange.backend.domain.event.DomainBalanceChanged;
import com.cryptoexchange.backend.domain.event.DomainTradeExecuted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to domain events and evicts relevant caches after transaction commit.
 * This ensures cache consistency with database state.
 */
@Component
public class CacheEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictionListener.class);
    private final CacheManager cacheManager;

    public CacheEvictionListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Evicts portfolio cache when a user's balance changes.
     * Executes AFTER_COMMIT to ensure database consistency.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBalanceChanged(DomainBalanceChanged event) {
        try {
            String userId = event.getUserId().toString();
            var portfolioCache = cacheManager.getCache("portfolio");
            if (portfolioCache != null) {
                portfolioCache.evict(userId);
                log.debug("Evicted portfolio cache for user {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to evict portfolio cache for user {}: {}", 
                event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Evicts market-related caches when a trade is executed.
     * Executes AFTER_COMMIT to ensure database consistency.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTradeExecuted(DomainTradeExecuted event) {
        try {
            String marketSymbol = event.getMarketSymbol();
            
            // Evict ticker cache for this market
            var tickerCache = cacheManager.getCache("ticker");
            if (tickerCache != null) {
                tickerCache.evict(marketSymbol);
                log.debug("Evicted ticker cache for market {}", marketSymbol);
            }
            
            // Evict recent trades cache for this market
            var recentTradesCache = cacheManager.getCache("recentTrades");
            if (recentTradesCache != null) {
                recentTradesCache.evict(marketSymbol);
                log.debug("Evicted recentTrades cache for market {}", marketSymbol);
            }
            
            // Evict order book cache for this market (if exists)
            var orderBookCache = cacheManager.getCache("orderBook");
            if (orderBookCache != null) {
                orderBookCache.evict(marketSymbol);
                log.debug("Evicted orderBook cache for market {}", marketSymbol);
            }
        } catch (Exception e) {
            log.error("Failed to evict market caches for trade {}: {}", 
                event.getTradeId(), e.getMessage(), e);
        }
    }
}
