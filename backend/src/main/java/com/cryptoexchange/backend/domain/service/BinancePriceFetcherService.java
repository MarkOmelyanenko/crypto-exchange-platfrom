package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.model.PriceTick;
import com.cryptoexchange.backend.domain.repository.PriceTickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled service that periodically fetches current prices from Binance
 * and stores them as PriceTick entries for dashboard price history.
 */
@Service
public class BinancePriceFetcherService {

    private static final Logger log = LoggerFactory.getLogger(BinancePriceFetcherService.class);

    private final BinanceService binanceService;
    private final PriceTickRepository priceTickRepository;

    @Value("${app.price-fetcher.symbols:BTC,ETH,SOL}")
    private String configuredSymbols;

    public BinancePriceFetcherService(BinanceService binanceService,
                                       PriceTickRepository priceTickRepository) {
        this.binanceService = binanceService;
        this.priceTickRepository = priceTickRepository;
    }

    /**
     * Fetch current prices from Binance every 30 seconds and store as price ticks.
     */
    @Scheduled(fixedDelayString = "${app.price-fetcher.interval-ms:30000}", initialDelay = 5000)
    public void fetchAndStorePrices() {
        String[] symbols = configuredSymbols.split(",");

        for (String rawSymbol : symbols) {
            String symbol = rawSymbol.trim().toUpperCase();
            try {
                String binanceSymbol = binanceService.toBinanceSymbol(symbol);
                BigDecimal price = binanceService.getCurrentPrice(binanceSymbol);

                if (price != null) {
                    PriceTick tick = new PriceTick(symbol, price, OffsetDateTime.now());
                    priceTickRepository.save(tick);
                    log.debug("Stored Binance price tick: {} = ${}", symbol, price);
                } else {
                    log.warn("Binance returned null price for {}", symbol);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch/store price for {}: {}", symbol, e.getMessage());
            }
        }
    }

    /**
     * Get the list of symbols being tracked.
     */
    public List<String> getTrackedSymbols() {
        return List.of(configuredSymbols.split(",")).stream()
            .map(s -> s.trim().toUpperCase())
            .toList();
    }
}
