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
    private final WhiteBitService whiteBitService;

    public DashboardService(BalanceRepository balanceRepository,
                           TradeRepository tradeRepository,
                           OrderRepository orderRepository,
                           TransactionRepository transactionRepository,
                           AssetService assetService,
                           MarketService marketService,
                           MarketTickRepository marketTickRepository,
                           PriceTickRepository priceTickRepository,
                           WhiteBitService whiteBitService) {
        this.balanceRepository = balanceRepository;
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.assetService = assetService;
        this.marketService = marketService;
        this.marketTickRepository = marketTickRepository;
        this.priceTickRepository = priceTickRepository;
        this.whiteBitService = whiteBitService;
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

        // Get current prices for all assets (in USDT)
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

                // Calculate average buy price and cost basis (USDT-converted)
                BigDecimal avgBuyPrice = calculateAverageBuyPrice(userId, asset.getId(), currentPrices);
                BigDecimal costBasis = quantity.multiply(avgBuyPrice, MC);
                totalCostBasis = totalCostBasis.add(costBasis);
                unrealizedPnlUsd = unrealizedPnlUsd.add(marketValue.subtract(costBasis, MC));
            }
        }

        // Calculate realized PnL from trades
        BigDecimal realizedPnlUsd = calculateRealizedPnl(userId, currentPrices);

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
            BigDecimal avgBuyPrice = calculateAverageBuyPrice(userId, asset.getId(), currentPrices);
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
     * Get recent transactions for user — merges legacy Orders, Transaction records,
     * and market-order Trade records, sorted by date descending, limited to {@code limit} entries.
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
                "COMPLETED"
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

        // 3) Market-order Trade records (from Trading page)
        Page<Trade> tradePage = tradeRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        for (Trade trade : tradePage.getContent()) {
            String pairSymbol = trade.getPair().getSymbol(); // e.g., "ETH/BTC"
            summaries.add(new TransactionSummary(
                trade.getId(),
                trade.getSide().name(),
                pairSymbol,
                trade.getBaseQty(),
                trade.getPrice(),
                trade.getCreatedAt(),
                "COMPLETED"
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
     * Calculate average buy price (in USDT) for an asset using weighted average cost basis.
     * Handles three kinds of records:
     *   1a) Trade records where the asset is the BASE (e.g. BTC in BTC/USDT or BTC/ETH)
     *   1b) Trade records where the asset is the QUOTE (e.g. BTC in ETH/BTC)
     *   2)  Transaction records (instant buy/sell, always USDT-denominated)
     *
     * For cross-pair trades (non-USDT quote), quoteQty is converted to USDT using
     * current prices (approximation — historical USDT price is not stored in trade records).
     *
     * @param currentPrices map of asset symbol → current USDT price
     */
    private BigDecimal calculateAverageBuyPrice(UUID userId, UUID assetId,
                                                 Map<String, BigDecimal> currentPrices) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO; // always in USDT

        // ── 1) Trade records (market orders + order-matching engine) ──
        List<Market> allMarkets = marketService.listActiveMarkets();

        // Markets where the tracked asset is the BASE (BUY = acquire, SELL = dispose)
        List<Market> baseMarkets = allMarkets.stream()
            .filter(m -> m.getBaseAsset().getId().equals(assetId))
            .collect(Collectors.toList());

        // Markets where the tracked asset is the QUOTE (SELL on market = receive quote = acquire)
        List<Market> quoteMarkets = allMarkets.stream()
            .filter(m -> m.getQuoteAsset().getId().equals(assetId))
            .collect(Collectors.toList());

        Set<UUID> quoteMarketIds = quoteMarkets.stream()
            .map(Market::getId).collect(Collectors.toSet());

        List<Trade> allTrades = new ArrayList<>();
        for (Market m : baseMarkets) {
            allTrades.addAll(tradeRepository.findAllByPairIdOrderByCreatedAtDesc(m.getId()));
        }
        for (Market m : quoteMarkets) {
            allTrades.addAll(tradeRepository.findAllByPairIdOrderByCreatedAtDesc(m.getId()));
        }
        allTrades.sort(Comparator.comparing(Trade::getCreatedAt));

        for (Trade trade : allTrades) {
            if (!trade.getUser().getId().equals(userId)) continue;

            boolean isQuoteMarket = quoteMarketIds.contains(trade.getPair().getId());

            if (isQuoteMarket) {
                // ── Asset is the QUOTE of this market ──
                // SELL on market → user sold base, received quote → ACQUIRE our asset
                // BUY on market → user bought base, spent quote → DISPOSE our asset
                if (trade.getSide() == OrderSide.SELL) {
                    BigDecimal qty = trade.getQuoteQty();
                    // Cost in USDT ≈ baseQty × current USDT price of the base asset
                    String baseSymbol = trade.getPair().getBaseAsset().getSymbol();
                    BigDecimal baseUsdPrice = currentPrices.getOrDefault(baseSymbol, BigDecimal.ZERO);
                    BigDecimal costUsd = trade.getBaseQty().multiply(baseUsdPrice, MC);
                    totalQuantity = totalQuantity.add(qty);
                    totalCost = totalCost.add(costUsd);
                } else { // BUY — user spent quote (our asset) → reduce position
                    BigDecimal qty = trade.getQuoteQty();
                    if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal avgCost = totalCost.divide(totalQuantity, MC);
                        BigDecimal costToRemove = qty.multiply(avgCost, MC);
                        totalQuantity = totalQuantity.subtract(qty);
                        totalCost = totalCost.subtract(costToRemove);
                        if (totalQuantity.compareTo(BigDecimal.ZERO) < 0) totalQuantity = BigDecimal.ZERO;
                        if (totalCost.compareTo(BigDecimal.ZERO) < 0) totalCost = BigDecimal.ZERO;
                    }
                }
            } else {
                // ── Asset is the BASE of this market (existing logic, with USDT conversion) ──
                String quoteSymbol = trade.getPair().getQuoteAsset().getSymbol();
                BigDecimal quoteToUsd = "USDT".equals(quoteSymbol) ? BigDecimal.ONE
                    : currentPrices.getOrDefault(quoteSymbol, BigDecimal.ZERO);

                if (trade.getSide() == OrderSide.BUY) {
                    BigDecimal qty = trade.getBaseQty();
                    BigDecimal costUsd = trade.getQuoteQty().multiply(quoteToUsd, MC);
                    totalQuantity = totalQuantity.add(qty);
                    totalCost = totalCost.add(costUsd);
                } else { // SELL
                    BigDecimal qty = trade.getBaseQty();
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
        }

        // ── 2) New Transaction records (instant buy/sell — always USDT-denominated) ──
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
     * Uses average cost method: when selling, realized PnL = (sellProceeds − avgCost × qty).
     *
     * Handles cross-pair trades by converting proceeds/costs to USDT using currentPrices.
     * Groups all trades for each base asset (across all markets) so the cost basis
     * is shared regardless of which market the asset was traded on.
     *
     * @param currentPrices map of asset symbol → current USDT price
     */
    private BigDecimal calculateRealizedPnl(UUID userId, Map<String, BigDecimal> currentPrices) {
        BigDecimal totalRealizedPnl = BigDecimal.ZERO;
        List<Market> allMarkets = marketService.listActiveMarkets();

        // Collect ALL trades the user participated in, across all markets
        Set<UUID> seenTradeIds = new HashSet<>();
        List<Trade> allUserTrades = new ArrayList<>();
        for (Market market : allMarkets) {
            for (Trade trade : tradeRepository.findAllByPairIdOrderByCreatedAtDesc(market.getId())) {
                if (trade.getUser().getId().equals(userId) && seenTradeIds.add(trade.getId())) {
                    allUserTrades.add(trade);
                }
            }
        }
        allUserTrades.sort(Comparator.comparing(Trade::getCreatedAt));

        // Group trades by the asset they affect: for each asset, track cost basis + realized PnL
        // An asset is affected if it's the BASE or the QUOTE of the trade's market.
        // We only compute realized PnL for the BASE-side position (buy base / sell base),
        // treating the quote-side as the "cash" leg. For cross-pair (non-USDT) quotes,
        // we convert to USDT.
        // We need per-asset grouping to maintain a single cost basis per asset.
        Map<UUID, List<Trade>> tradesByBaseAsset = new LinkedHashMap<>();

        for (Trade trade : allUserTrades) {
            UUID baseAssetId = trade.getPair().getBaseAsset().getId();
            tradesByBaseAsset.computeIfAbsent(baseAssetId, k -> new ArrayList<>()).add(trade);
        }

        for (Map.Entry<UUID, List<Trade>> entry : tradesByBaseAsset.entrySet()) {
            BigDecimal costBasis = BigDecimal.ZERO; // in USDT
            BigDecimal quantity = BigDecimal.ZERO;

            for (Trade trade : entry.getValue()) {
                String quoteSymbol = trade.getPair().getQuoteAsset().getSymbol();
                BigDecimal quoteToUsd = "USDT".equals(quoteSymbol) ? BigDecimal.ONE
                    : currentPrices.getOrDefault(quoteSymbol, BigDecimal.ZERO);

                if (trade.getSide() == OrderSide.BUY) {
                    BigDecimal qty = trade.getBaseQty();
                    BigDecimal costUsd = trade.getQuoteQty().multiply(quoteToUsd, MC);
                    costBasis = costBasis.add(costUsd);
                    quantity = quantity.add(qty);
                } else { // SELL
                    BigDecimal qty = trade.getBaseQty();
                    BigDecimal proceedsUsd = trade.getQuoteQty().multiply(quoteToUsd, MC);
                    if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                        // Only calculate realized PnL for the quantity we actually have in cost basis
                        BigDecimal qtyToSell = qty.min(quantity);
                        BigDecimal avgCost = costBasis.divide(quantity, MC);
                        BigDecimal costOfSold = qtyToSell.multiply(avgCost, MC);
                        
                        // Prorate proceeds if selling more than available (shouldn't happen, but handle gracefully)
                        BigDecimal proratedProceeds = qtyToSell.compareTo(qty) < 0
                            ? proceedsUsd.multiply(qtyToSell.divide(qty, MC), MC)
                            : proceedsUsd;
                        
                        totalRealizedPnl = totalRealizedPnl.add(proratedProceeds.subtract(costOfSold, MC));
                        costBasis = costBasis.subtract(costOfSold, MC);
                        quantity = quantity.subtract(qtyToSell, MC);
                        if (quantity.compareTo(BigDecimal.ZERO) < 0) quantity = BigDecimal.ZERO;
                        if (costBasis.compareTo(BigDecimal.ZERO) < 0) costBasis = BigDecimal.ZERO;
                    }
                }
            }
        }

        // ── 2) New Transaction records (all assets at once — always USDT) ──
        try {
            Page<Transaction> txPage = transactionRepository.findFiltered(
                    userId, null, null, null, null,
                    PageRequest.of(0, 5000, Sort.by(Sort.Direction.ASC, "createdAt")));

            // Group by asset symbol and process per-asset cost basis
            Map<String, List<Transaction>> byAsset = txPage.getContent().stream()
                    .collect(Collectors.groupingBy(Transaction::getAssetSymbol));

            for (Map.Entry<String, List<Transaction>> txEntry : byAsset.entrySet()) {
                BigDecimal costBasis = BigDecimal.ZERO;
                BigDecimal quantity = BigDecimal.ZERO;

                for (Transaction tx : txEntry.getValue()) {
                    if (tx.getSide() == OrderSide.BUY) {
                        costBasis = costBasis.add(tx.getTotalUsd());
                        quantity = quantity.add(tx.getQuantity());
                    } else { // SELL
                        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                            // Only calculate realized PnL for the quantity we actually have in cost basis
                            BigDecimal qtyToSell = tx.getQuantity().min(quantity);
                            BigDecimal avgCost = costBasis.divide(quantity, MC);
                            BigDecimal costOfSold = qtyToSell.multiply(avgCost, MC);
                            
                            // Prorate proceeds if selling more than available (shouldn't happen, but handle gracefully)
                            BigDecimal proratedProceeds = qtyToSell.compareTo(tx.getQuantity()) < 0
                                ? tx.getTotalUsd().multiply(qtyToSell.divide(tx.getQuantity(), MC), MC)
                                : tx.getTotalUsd();
                            
                            totalRealizedPnl = totalRealizedPnl.add(proratedProceeds.subtract(costOfSold, MC));
                            costBasis = costBasis.subtract(costOfSold, MC);
                            quantity = quantity.subtract(qtyToSell, MC);
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
     * Uses a single batch WhiteBit API call (fetches ALL tickers at once, cached 5s)
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

        // 1) Batch-fetch from WhiteBit (single API call for ALL tickers, cached 5s)
        try {
            List<String> whiteBitSymbols = symbols.stream()
                .map(whiteBitService::toWhiteBitSymbol)
                .collect(Collectors.toList());
            Map<String, WhiteBitService.WhiteBitTicker24h> tickers =
                whiteBitService.getBatchTicker24h(whiteBitSymbols);

            for (String symbol : symbols) {
                String whiteBitSymbol = whiteBitService.toWhiteBitSymbol(symbol);
                WhiteBitService.WhiteBitTicker24h ticker = tickers.get(whiteBitSymbol);
                if (ticker != null && ticker.lastPrice != null) {
                    try {
                        prices.put(symbol, new BigDecimal(ticker.lastPrice));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid price format from WhiteBit for {}: {}", symbol, ticker.lastPrice);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to batch-fetch prices from WhiteBit: {}", e.getMessage());
        }

        // 2) Fallback to PriceTick DB (WhiteBit-fetched ticks stored by scheduled job)
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
