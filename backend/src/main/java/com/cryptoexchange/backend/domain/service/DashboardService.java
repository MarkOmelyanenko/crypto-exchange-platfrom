package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);

    private final BalanceRepository balanceRepository;
    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final AssetService assetService;
    private final MarketService marketService;
    private final MarketTickRepository marketTickRepository;
    private final PriceTickRepository priceTickRepository;
    private final BinanceService binanceService;

    public DashboardService(BalanceRepository balanceRepository,
                           TradeRepository tradeRepository,
                           OrderRepository orderRepository,
                           TransactionRepository transactionRepository,
                           AssetService assetService,
                           MarketService marketService,
                           MarketTickRepository marketTickRepository,
                           PriceTickRepository priceTickRepository,
                           BinanceService binanceService) {
        this.balanceRepository = balanceRepository;
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.assetService = assetService;
        this.marketService = marketService;
        this.marketTickRepository = marketTickRepository;
        this.priceTickRepository = priceTickRepository;
        this.binanceService = binanceService;
    }

    /**
     * Get dashboard summary: total value, cash, PnL
     */
    @Transactional(readOnly = true)
    public DashboardSummary getSummary(UUID userId) {
        List<Balance> balances = balanceRepository.findAllByUserId(userId);

        // Find USDT asset safely — brand-new setups may not have it yet
        UUID usdtAssetId = null;
        try {
            usdtAssetId = assetService.getAssetBySymbol("USDT").getId();
        } catch (Exception e) {
            log.debug("USDT asset not found; treating all balances as non-cash");
        }

        BigDecimal availableCashUsd = BigDecimal.ZERO;
        BigDecimal totalValueUsd = BigDecimal.ZERO;
        BigDecimal unrealizedPnlUsd = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;

        // Get current prices for all assets
        Map<String, BigDecimal> currentPrices = getCurrentPrices();

        // Calculate holdings value and cost basis
        for (Balance balance : balances) {
            Asset asset = balance.getAsset();
            BigDecimal quantity = balance.getAvailable().add(balance.getLocked());

            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (usdtAssetId != null && asset.getId().equals(usdtAssetId)) {
                // USDT is already in USD
                availableCashUsd = availableCashUsd.add(quantity);
                totalValueUsd = totalValueUsd.add(quantity);
            } else {
                // Get current price in USDT
                BigDecimal currentPrice = currentPrices.getOrDefault(asset.getSymbol(), BigDecimal.ZERO);
                BigDecimal marketValue = quantity.multiply(currentPrice, MC);
                totalValueUsd = totalValueUsd.add(marketValue);

                // Calculate average buy price and cost basis
                BigDecimal avgBuyPrice = calculateAverageBuyPrice(userId, asset.getId());
                BigDecimal costBasis = quantity.multiply(avgBuyPrice, MC);
                totalCostBasis = totalCostBasis.add(costBasis);
                unrealizedPnlUsd = unrealizedPnlUsd.add(marketValue.subtract(costBasis, MC));
            }
        }

        // Calculate realized PnL from trades
        BigDecimal realizedPnlUsd = calculateRealizedPnl(userId);

        // Calculate unrealized PnL percentage
        BigDecimal unrealizedPnlPercent = BigDecimal.ZERO;
        if (totalCostBasis.compareTo(BigDecimal.ZERO) > 0) {
            unrealizedPnlPercent = unrealizedPnlUsd
                .divide(totalCostBasis, MC)
                .multiply(BigDecimal.valueOf(100), MC);
        }

        return new DashboardSummary(
            totalValueUsd.setScale(2, RoundingMode.HALF_UP),
            availableCashUsd.setScale(2, RoundingMode.HALF_UP),
            unrealizedPnlUsd.setScale(2, RoundingMode.HALF_UP),
            unrealizedPnlPercent.setScale(2, RoundingMode.HALF_UP),
            realizedPnlUsd.setScale(2, RoundingMode.HALF_UP)
        );
    }

    /**
     * Get user holdings with current prices and PnL
     */
    @Transactional(readOnly = true)
    public List<Holding> getHoldings(UUID userId) {
        List<Balance> balances = balanceRepository.findAllByUserId(userId);

        UUID usdtAssetId = null;
        try {
            usdtAssetId = assetService.getAssetBySymbol("USDT").getId();
        } catch (Exception e) {
            log.debug("USDT asset not found; no balances will be excluded as cash");
        }

        Map<String, BigDecimal> currentPrices = getCurrentPrices();
        List<Holding> holdings = new ArrayList<>();

        for (Balance balance : balances) {
            Asset asset = balance.getAsset();
            BigDecimal quantity = balance.getAvailable().add(balance.getLocked());

            // Skip zero balances and USDT (cash)
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (usdtAssetId != null && asset.getId().equals(usdtAssetId)) {
                continue;
            }

            BigDecimal currentPrice = currentPrices.getOrDefault(asset.getSymbol(), BigDecimal.ZERO);
            BigDecimal avgBuyPrice = calculateAverageBuyPrice(userId, asset.getId());
            BigDecimal marketValue = quantity.multiply(currentPrice, MC);
            BigDecimal costBasis = quantity.multiply(avgBuyPrice, MC);
            BigDecimal unrealizedPnl = marketValue.subtract(costBasis, MC);
            BigDecimal unrealizedPnlPercent = BigDecimal.ZERO;
            if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                unrealizedPnlPercent = unrealizedPnl
                    .divide(costBasis, MC)
                    .multiply(BigDecimal.valueOf(100), MC);
            }

            holdings.add(new Holding(
                asset.getId(),
                asset.getSymbol(),
                asset.getName(),
                quantity.setScale(asset.getScale(), RoundingMode.HALF_UP),
                avgBuyPrice.setScale(2, RoundingMode.HALF_UP),
                currentPrice.setScale(2, RoundingMode.HALF_UP),
                marketValue.setScale(2, RoundingMode.HALF_UP),
                unrealizedPnl.setScale(2, RoundingMode.HALF_UP),
                unrealizedPnlPercent.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return holdings;
    }

    /**
     * Get recent transactions for user — merges legacy Orders and new Transaction records,
     * sorted by date descending, limited to {@code limit} entries.
     */
    @Transactional(readOnly = true)
    public List<TransactionSummary> getRecentTransactions(UUID userId, int limit) {
        List<TransactionSummary> summaries = new ArrayList<>();

        // 1) New Transaction entity records
        Page<Transaction> txPage = transactionRepository.findAllByUserId(
                userId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        for (Transaction tx : txPage.getContent()) {
            summaries.add(new TransactionSummary(
                tx.getId(),
                tx.getSide().name(),
                tx.getAssetSymbol(),
                tx.getQuantity(),
                tx.getPriceUsd().setScale(2, RoundingMode.HALF_UP),
                tx.getCreatedAt(),
                "COMPLETED" // MVP transactions are always immediately completed
            ));
        }

        // 2) Legacy Order-based records
        Page<Order> ordersPage = orderRepository.findAllByUserId(
                userId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        for (Order order : ordersPage.getContent()) {
            Market market = order.getMarket();
            Asset baseAsset = market.getBaseAsset();
            String symbol = baseAsset.getSymbol();
            String type = order.getSide().name();

            BigDecimal quantity = order.getFilledAmount().compareTo(BigDecimal.ZERO) > 0
                    ? order.getFilledAmount()
                    : order.getAmount();
            BigDecimal price = order.getPrice() != null ? order.getPrice() : BigDecimal.ZERO;

            String status = mapOrderStatus(order.getStatus());

            summaries.add(new TransactionSummary(
                order.getId(),
                type,
                symbol,
                quantity.setScale(baseAsset.getScale(), RoundingMode.HALF_UP),
                price.setScale(2, RoundingMode.HALF_UP),
                order.getCreatedAt(),
                status
            ));
        }

        // Sort merged list by timestamp descending & limit
        summaries.sort(Comparator.comparing((TransactionSummary s) -> s.timestamp).reversed());
        return summaries.stream().limit(limit).collect(Collectors.toList());
    }

    private String mapOrderStatus(OrderStatus status) {
        return switch (status) {
            case FILLED -> "COMPLETED";
            case NEW, PARTIALLY_FILLED -> "PENDING";
            case CANCELED -> "CANCELLED";
            case REJECTED -> "FAILED";
        };
    }

    /**
     * Calculate average buy price for an asset using weighted average cost basis.
     * Combines legacy Trade records (order-matching engine) and new Transaction records
     * (instant buy/sell), sorted chronologically to maintain correct cost basis.
     */
    private BigDecimal calculateAverageBuyPrice(UUID userId, UUID assetId) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        // ── 1) Legacy Trade records (order-matching engine) ──
        List<Market> markets = marketService.listActiveMarkets().stream()
            .filter(m -> m.getBaseAsset().getId().equals(assetId))
            .collect(Collectors.toList());

        List<Trade> allTrades = new ArrayList<>();
        for (Market market : markets) {
            allTrades.addAll(tradeRepository.findAllByMarketIdOrderByExecutedAtDesc(market.getId()));
        }
        allTrades.sort(Comparator.comparing(Trade::getExecutedAt));

        for (Trade trade : allTrades) {
            Order buyerOrder = trade.getMakerOrder().getSide() == OrderSide.BUY 
                ? trade.getMakerOrder() 
                : trade.getTakerOrder();
            Order sellerOrder = trade.getMakerOrder().getSide() == OrderSide.SELL 
                ? trade.getMakerOrder() 
                : trade.getTakerOrder();

            if (buyerOrder.getUser().getId().equals(userId)) {
                BigDecimal qty = trade.getAmount();
                BigDecimal cost = trade.getQuoteAmount();
                totalQuantity = totalQuantity.add(qty);
                totalCost = totalCost.add(cost);
            } else if (sellerOrder.getUser().getId().equals(userId)) {
                BigDecimal qty = trade.getAmount();
                if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal avgCost = totalCost.divide(totalQuantity, MC);
                    BigDecimal costToRemove = qty.multiply(avgCost, MC);
                    totalQuantity = totalQuantity.subtract(qty);
                    totalCost = totalCost.subtract(costToRemove);
                    if (totalQuantity.compareTo(BigDecimal.ZERO) < 0) totalQuantity = BigDecimal.ZERO;
                    if (totalCost.compareTo(BigDecimal.ZERO) < 0) totalCost = BigDecimal.ZERO;
                }
            }
        }

        // ── 2) New Transaction records (instant buy/sell) ──
        try {
            Asset asset = assetService.getAsset(assetId);
            Page<Transaction> txPage = transactionRepository.findFiltered(
                    userId, asset.getSymbol(), null, null, null,
                    PageRequest.of(0, 1000, Sort.by(Sort.Direction.ASC, "createdAt")));

            for (Transaction tx : txPage.getContent()) {
                if (tx.getSide() == OrderSide.BUY) {
                    totalQuantity = totalQuantity.add(tx.getQuantity());
                    totalCost = totalCost.add(tx.getTotalUsd());
                } else { // SELL
                    if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal avgCost = totalCost.divide(totalQuantity, MC);
                        BigDecimal costToRemove = tx.getQuantity().multiply(avgCost, MC);
                        totalQuantity = totalQuantity.subtract(tx.getQuantity());
                        totalCost = totalCost.subtract(costToRemove);
                        if (totalQuantity.compareTo(BigDecimal.ZERO) < 0) totalQuantity = BigDecimal.ZERO;
                        if (totalCost.compareTo(BigDecimal.ZERO) < 0) totalCost = BigDecimal.ZERO;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not load Transaction records for asset {}: {}", assetId, e.getMessage());
        }

        if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            return totalCost.divide(totalQuantity, MC);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calculate realized PnL from completed sell trades and sell transactions.
     * Uses average cost method: when selling, realized PnL = (sellPrice - avgCost) * quantity
     */
    private BigDecimal calculateRealizedPnl(UUID userId) {
        List<Market> markets = marketService.listActiveMarkets();
        BigDecimal totalRealizedPnl = BigDecimal.ZERO;

        // ── 1) Legacy Trade records per market ──
        for (Market market : markets) {
            List<Trade> trades = tradeRepository.findAllByMarketIdOrderByExecutedAtDesc(market.getId());
            trades.sort(Comparator.comparing(Trade::getExecutedAt));

            BigDecimal costBasis = BigDecimal.ZERO;
            BigDecimal quantity = BigDecimal.ZERO;

            for (Trade trade : trades) {
                Order buyerOrder = trade.getMakerOrder().getSide() == OrderSide.BUY 
                    ? trade.getMakerOrder() 
                    : trade.getTakerOrder();
                Order sellerOrder = trade.getMakerOrder().getSide() == OrderSide.SELL 
                    ? trade.getMakerOrder() 
                    : trade.getTakerOrder();

                if (buyerOrder.getUser().getId().equals(userId)) {
                    BigDecimal qty = trade.getAmount();
                    BigDecimal cost = trade.getQuoteAmount();
                    costBasis = costBasis.add(cost);
                    quantity = quantity.add(qty);
                } else if (sellerOrder.getUser().getId().equals(userId)) {
                    BigDecimal qty = trade.getAmount();
                    BigDecimal sellProceeds = trade.getQuoteAmount();
                    if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal avgCost = costBasis.divide(quantity, MC);
                        BigDecimal costOfSold = qty.multiply(avgCost, MC);
                        totalRealizedPnl = totalRealizedPnl.add(sellProceeds.subtract(costOfSold, MC));
                        costBasis = costBasis.subtract(costOfSold, MC);
                        quantity = quantity.subtract(qty, MC);
                        if (quantity.compareTo(BigDecimal.ZERO) < 0) quantity = BigDecimal.ZERO;
                        if (costBasis.compareTo(BigDecimal.ZERO) < 0) costBasis = BigDecimal.ZERO;
                    }
                }
            }
        }

        // ── 2) New Transaction records (all assets at once) ──
        try {
            Page<Transaction> txPage = transactionRepository.findFiltered(
                    userId, null, null, null, null,
                    PageRequest.of(0, 5000, Sort.by(Sort.Direction.ASC, "createdAt")));

            // Group by asset symbol and process per-asset cost basis
            Map<String, List<Transaction>> byAsset = txPage.getContent().stream()
                    .collect(Collectors.groupingBy(Transaction::getAssetSymbol));

            for (Map.Entry<String, List<Transaction>> entry : byAsset.entrySet()) {
                BigDecimal costBasis = BigDecimal.ZERO;
                BigDecimal quantity = BigDecimal.ZERO;

                for (Transaction tx : entry.getValue()) {
                    if (tx.getSide() == OrderSide.BUY) {
                        costBasis = costBasis.add(tx.getTotalUsd());
                        quantity = quantity.add(tx.getQuantity());
                    } else { // SELL
                        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal avgCost = costBasis.divide(quantity, MC);
                            BigDecimal costOfSold = tx.getQuantity().multiply(avgCost, MC);
                            totalRealizedPnl = totalRealizedPnl.add(tx.getTotalUsd().subtract(costOfSold, MC));
                            costBasis = costBasis.subtract(costOfSold, MC);
                            quantity = quantity.subtract(tx.getQuantity(), MC);
                            if (quantity.compareTo(BigDecimal.ZERO) < 0) quantity = BigDecimal.ZERO;
                            if (costBasis.compareTo(BigDecimal.ZERO) < 0) costBasis = BigDecimal.ZERO;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not calculate realized PnL from Transaction records: {}", e.getMessage());
        }

        return totalRealizedPnl;
    }

    /**
     * Get current prices for all assets.
     * Uses a single batch Binance API call (fetches ALL tickers at once, cached 5s)
     * with fallback to PriceTick DB and MarketTick DB.
     */
    private Map<String, BigDecimal> getCurrentPrices() {
        Map<String, BigDecimal> prices = new HashMap<>();

        // Collect all relevant asset symbols from active markets
        List<Market> markets = marketService.listActiveMarkets();
        Set<String> symbols = new HashSet<>();
        for (Market market : markets) {
            symbols.add(market.getBaseAsset().getSymbol());
        }

        // 1) Batch-fetch from Binance (single API call for ALL tickers, cached 5s)
        try {
            List<String> binanceSymbols = symbols.stream()
                .map(binanceService::toBinanceSymbol)
                .collect(Collectors.toList());
            Map<String, BinanceService.BinanceTicker24h> tickers =
                binanceService.getBatchTicker24h(binanceSymbols);

            for (String symbol : symbols) {
                String binanceSymbol = binanceService.toBinanceSymbol(symbol);
                BinanceService.BinanceTicker24h ticker = tickers.get(binanceSymbol);
                if (ticker != null && ticker.lastPrice != null) {
                    try {
                        prices.put(symbol, new BigDecimal(ticker.lastPrice));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid price format from Binance for {}: {}", symbol, ticker.lastPrice);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to batch-fetch prices from Binance: {}", e.getMessage());
        }

        // 2) Fallback to PriceTick DB (Binance-fetched ticks stored by scheduled job)
        for (String symbol : symbols) {
            if (!prices.containsKey(symbol)) {
                priceTickRepository.findFirstBySymbolOrderByTsDesc(symbol)
                    .ifPresent(tick -> {
                        prices.put(symbol, tick.getPriceUsd());
                        log.debug("Price fallback to PriceTick for {}: {}", symbol, tick.getPriceUsd());
                    });
            }
        }

        // 3) Fallback to MarketTick DB (simulator ticks)
        for (Market market : markets) {
            String baseSymbol = market.getBaseAsset().getSymbol();
            if (!prices.containsKey(baseSymbol)) {
                marketTickRepository.findFirstByMarketSymbolOrderByTsDesc(market.getSymbol())
                    .ifPresent(tick -> {
                        prices.put(baseSymbol, tick.getLastPrice());
                        log.debug("Price fallback to MarketTick for {}: {}", baseSymbol, tick.getLastPrice());
                    });
            }
        }

        return prices;
    }

    // DTOs
    public static class DashboardSummary {
        public final BigDecimal totalValueUsd;
        public final BigDecimal availableCashUsd;
        public final BigDecimal unrealizedPnlUsd;
        public final BigDecimal unrealizedPnlPercent;
        public final BigDecimal realizedPnlUsd;

        public DashboardSummary(BigDecimal totalValueUsd, BigDecimal availableCashUsd,
                               BigDecimal unrealizedPnlUsd, BigDecimal unrealizedPnlPercent,
                               BigDecimal realizedPnlUsd) {
            this.totalValueUsd = totalValueUsd;
            this.availableCashUsd = availableCashUsd;
            this.unrealizedPnlUsd = unrealizedPnlUsd;
            this.unrealizedPnlPercent = unrealizedPnlPercent;
            this.realizedPnlUsd = realizedPnlUsd;
        }
    }

    public static class Holding {
        public final UUID assetId;
        public final String symbol;
        public final String name;
        public final BigDecimal quantity;
        public final BigDecimal avgBuyPriceUsd;
        public final BigDecimal currentPriceUsd;
        public final BigDecimal marketValueUsd;
        public final BigDecimal unrealizedPnlUsd;
        public final BigDecimal unrealizedPnlPercent;

        public Holding(UUID assetId, String symbol, String name, BigDecimal quantity,
                      BigDecimal avgBuyPriceUsd, BigDecimal currentPriceUsd,
                      BigDecimal marketValueUsd, BigDecimal unrealizedPnlUsd,
                      BigDecimal unrealizedPnlPercent) {
            this.assetId = assetId;
            this.symbol = symbol;
            this.name = name;
            this.quantity = quantity;
            this.avgBuyPriceUsd = avgBuyPriceUsd;
            this.currentPriceUsd = currentPriceUsd;
            this.marketValueUsd = marketValueUsd;
            this.unrealizedPnlUsd = unrealizedPnlUsd;
            this.unrealizedPnlPercent = unrealizedPnlPercent;
        }
    }

    public static class TransactionSummary {
        public final UUID id;
        public final String type;
        public final String symbol;
        public final BigDecimal quantity;
        public final BigDecimal priceUsd;
        public final OffsetDateTime timestamp;
        public final String status;

        public TransactionSummary(UUID id, String type, String symbol, BigDecimal quantity,
                                BigDecimal priceUsd, OffsetDateTime timestamp, String status) {
            this.id = id;
            this.type = type;
            this.symbol = symbol;
            this.quantity = quantity;
            this.priceUsd = priceUsd;
            this.timestamp = timestamp;
            this.status = status;
        }
    }
}
