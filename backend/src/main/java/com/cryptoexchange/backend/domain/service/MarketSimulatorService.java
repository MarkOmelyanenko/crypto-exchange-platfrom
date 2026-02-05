package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.model.Market;
import com.cryptoexchange.backend.domain.model.MarketTick;
import com.cryptoexchange.backend.domain.model.MarketTrade;
import com.cryptoexchange.backend.domain.repository.MarketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Market simulator service that generates realistic market data.
 * Runs on a scheduled interval and can be started/stopped dynamically.
 */
@Service
@ConditionalOnProperty(name = "app.simulator.enabled", havingValue = "true", matchIfMissing = false)
public class MarketSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(MarketSimulatorService.class);

    private final MarketRepository marketRepository;
    private final MarketTickStore tickStore;
    private final MarketTradeStore tradeStore;
    private final MarketSimulationEngine simulationEngine;
    private final MarketSnapshotStore snapshotStore;
    private final MarketEventPublisher eventPublisher;

    @Value("${app.simulator.seed:42}")
    private long seed;

    @Value("${app.simulator.markets:BTC-USDT,ETH-USDT}")
    private String marketsConfig;

    @Value("${app.simulator.spread-bps:8}")
    private BigDecimal spreadBps;

    @Value("${app.simulator.avg-trades-per-tick:2}")
    private int avgTradesPerTick;

    @Value("${app.simulator.min-qty:0.001}")
    private BigDecimal minQty;

    @Value("${app.simulator.max-qty:1.0}")
    private BigDecimal maxQty;

    @Value("${app.simulator.persist-every-n-ticks:1}")
    private int persistEveryNTicks;

    @Value("${app.simulator.auto-start:true}")
    private boolean autoStart;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, BigDecimal> initialPrices = new HashMap<>();
    private final Map<String, BigDecimal> volatilities = new HashMap<>();
    private final Map<String, Integer> tickCounters = new java.util.concurrent.ConcurrentHashMap<>();

    public MarketSimulatorService(
            MarketRepository marketRepository,
            MarketTickStore tickStore,
            MarketTradeStore tradeStore,
            MarketSimulationEngine simulationEngine,
            MarketSnapshotStore snapshotStore,
            MarketEventPublisher eventPublisher) {
        this.marketRepository = marketRepository;
        this.tickStore = tickStore;
        this.tradeStore = tradeStore;
        this.simulationEngine = simulationEngine;
        this.snapshotStore = snapshotStore;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        loadConfiguration();
        initializeMarkets();
        if (autoStart) {
            start();
        }
    }

    private void loadConfiguration() {
        // Parse initial prices from config (format: app.simulator.initial-prices.BTC-USDT=65000)
        // This would be done via @Value with SpEL, but for simplicity we'll use a map
        // In production, you'd use @ConfigurationProperties
        
        // Parse volatilities (format: app.simulator.volatility.BTC-USDT=0.012)
        // For now, we'll use defaults and allow override via properties
        
        log.info("Market simulator initialized with seed: {}, markets: {}", seed, marketsConfig);
    }

    private void initializeMarkets() {
        String[] marketSymbols = marketsConfig.split(",");
        for (String symbol : marketSymbols) {
            symbol = symbol.trim();
            Optional<Market> marketOpt = marketRepository.findBySymbol(symbol);
            if (marketOpt.isEmpty()) {
                log.warn("Market {} not found in database, skipping", symbol);
                continue;
            }

            // Get initial price from config or use default
            BigDecimal initialPrice = initialPrices.getOrDefault(symbol, getDefaultInitialPrice(symbol));
            BigDecimal volatility = volatilities.getOrDefault(symbol, BigDecimal.valueOf(0.012)); // 1.2% default

            // Initialize with unique seed per market (seed + hash of symbol)
            long marketSeed = seed + symbol.hashCode();
            simulationEngine.initializeMarket(symbol, initialPrice, marketSeed);
            tickCounters.put(symbol, 0);
            
            log.info("Initialized market {} with price {} and volatility {}", symbol, initialPrice, volatility);
        }
    }

    private BigDecimal getDefaultInitialPrice(String symbol) {
        // Simple defaults based on symbol
        if (symbol.contains("BTC")) {
            return BigDecimal.valueOf(65000);
        } else if (symbol.contains("ETH")) {
            return BigDecimal.valueOf(3500);
        }
        return BigDecimal.valueOf(100); // Default
    }

    @Scheduled(fixedDelayString = "${app.simulator.tick-interval-ms:1000}")
    public void tick() {
        if (!running.get()) {
            return;
        }

        String[] marketSymbols = marketsConfig.split(",");
        for (String symbol : marketSymbols) {
            symbol = symbol.trim();
            try {
                processMarketTick(symbol);
            } catch (Exception e) {
                log.error("Error processing tick for market {}: {}", symbol, e.getMessage(), e);
            }
        }
    }

    /**
     * Execute one tick for a single market (for testing).
     * This method performs one cycle without scheduling.
     */
    @Transactional
    public void runOneTick(String marketSymbol) {
        processMarketTick(marketSymbol);
    }

    @Transactional
    public void processMarketTick(String marketSymbol) {
        Optional<Market> marketOpt = marketRepository.findBySymbol(marketSymbol);
        if (marketOpt.isEmpty() || !marketOpt.get().getActive()) {
            return;
        }

        BigDecimal volatility = volatilities.getOrDefault(marketSymbol, BigDecimal.valueOf(0.012));
        int tickCount = tickCounters.getOrDefault(marketSymbol, 0);
        tickCounters.put(marketSymbol, tickCount + 1);

        // Generate tick
        MarketSimulationEngine.TickData tickData = simulationEngine.generateTick(marketSymbol, volatility, spreadBps);

        // Generate trades
        Random rng = new Random(seed + marketSymbol.hashCode() + tickCount);
        List<MarketSimulationEngine.TradeData> tradeDataList = simulationEngine.generateTrades(
            marketSymbol, tickData.lastPrice, avgTradesPerTick, minQty, maxQty, rng
        );

        // Persist to DB (optionally skip some ticks)
        if (tickCount % persistEveryNTicks == 0) {
            MarketTick tick = new MarketTick(
                marketSymbol, tickData.ts, tickData.lastPrice, tickData.bid, tickData.ask, tickData.volume
            );
            tickStore.saveAll(List.of(tick));
        }

        // Always persist trades
        List<MarketTrade> trades = tradeDataList.stream()
            .map(tradeData -> new MarketTrade(
                marketSymbol, tradeData.ts, tradeData.price, tradeData.qty, tradeData.side
            ))
            .toList();
        tradeStore.saveAll(trades);

        // Update snapshots
        snapshotStore.saveTicker(marketSymbol, tickData);
        snapshotStore.saveRecentTrades(marketSymbol, tradeDataList);

        // Publish events
        eventPublisher.publishTick(marketSymbol, tickData);
        for (MarketSimulationEngine.TradeData tradeData : tradeDataList) {
            eventPublisher.publishTrade(marketSymbol, tradeData);
        }

        log.debug("Processed tick for {}: price={}, trades={}", marketSymbol, tickData.lastPrice, tradeDataList.size());
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Market simulator started");
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Market simulator stopped");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", true);
        status.put("running", running.get());
        status.put("seed", seed);
        status.put("markets", Arrays.asList(marketsConfig.split(",")));
        status.put("tickInterval", "1000ms"); // Could be made configurable
        return status;
    }

    // Configuration setters (for property binding)
    public void setInitialPrice(String marketSymbol, BigDecimal price) {
        initialPrices.put(marketSymbol, price);
    }

    public void setVolatility(String marketSymbol, BigDecimal volatility) {
        volatilities.put(marketSymbol, volatility);
    }
}
