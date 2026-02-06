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
    private final AssetService assetService;
    private final MarketService marketService;
    private final MarketTickRepository marketTickRepository;
    private final PriceTickRepository priceTickRepository;
    private final BinanceService binanceService;

    public DashboardService(BalanceRepository balanceRepository,
                           TradeRepository tradeRepository,
                           OrderRepository orderRepository,
                           AssetService assetService,
                           MarketService marketService,
                           MarketTickRepository marketTickRepository,
                           PriceTickRepository priceTickRepository,
                           BinanceService binanceService) {
        this.balanceRepository = balanceRepository;
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
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
     * Get recent transactions for user (paginated at DB level for efficiency).
     */
    @Transactional(readOnly = true)
    public List<TransactionSummary> getRecentTransactions(UUID userId, int limit) {
        Page<Order> ordersPage = orderRepository.findAllByUserId(
                userId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        return ordersPage.getContent().stream()
            .map(order -> {
                Market market = order.getMarket();
                Asset baseAsset = market.getBaseAsset();
                String symbol = baseAsset.getSymbol();
                String type = order.getSide().name();

                // Show filledAmount for filled orders, otherwise the original order amount
                BigDecimal quantity = order.getFilledAmount().compareTo(BigDecimal.ZERO) > 0
                        ? order.getFilledAmount()
                        : order.getAmount();
                BigDecimal price = order.getPrice() != null ? order.getPrice() : BigDecimal.ZERO;

                // Map internal statuses to user-friendly labels
                String status = mapOrderStatus(order.getStatus());

                return new TransactionSummary(
                    order.getId(),
                    type,
                    symbol,
                    quantity.setScale(baseAsset.getScale(), RoundingMode.HALF_UP),
                    price.setScale(2, RoundingMode.HALF_UP),
                    order.getCreatedAt(),
                    status
                );
            })
            .collect(Collectors.toList());
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
     * Calculate average buy price for an asset using weighted average cost basis
     * Simplified: tracks net position and cost basis
     */
    private BigDecimal calculateAverageBuyPrice(UUID userId, UUID assetId) {
        // Find all markets where this asset is the base
        List<Market> markets = marketService.listActiveMarkets().stream()
            .filter(m -> m.getBaseAsset().getId().equals(assetId))
            .collect(Collectors.toList());

        if (markets.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        // Process all trades chronologically to calculate cost basis
        List<Trade> allTrades = new ArrayList<>();
        for (Market market : markets) {
            allTrades.addAll(tradeRepository.findAllByMarketIdOrderByExecutedAtDesc(market.getId()));
        }
        
        // Sort by execution time (oldest first for FIFO)
        allTrades.sort(Comparator.comparing(Trade::getExecutedAt));

        for (Trade trade : allTrades) {
            Order buyerOrder = trade.getMakerOrder().getSide() == OrderSide.BUY 
                ? trade.getMakerOrder() 
                : trade.getTakerOrder();
            Order sellerOrder = trade.getMakerOrder().getSide() == OrderSide.SELL 
                ? trade.getMakerOrder() 
                : trade.getTakerOrder();

            if (buyerOrder.getUser().getId().equals(userId)) {
                // User bought - add to cost basis
                BigDecimal qty = trade.getAmount();
                BigDecimal cost = trade.getQuoteAmount();
                totalQuantity = totalQuantity.add(qty);
                totalCost = totalCost.add(cost);
            } else if (sellerOrder.getUser().getId().equals(userId)) {
                // User sold - reduce quantity and cost basis using average cost
                BigDecimal qty = trade.getAmount();
                if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal avgCost = totalCost.divide(totalQuantity, MC);
                    BigDecimal costToRemove = qty.multiply(avgCost, MC);
                    totalQuantity = totalQuantity.subtract(qty);
                    totalCost = totalCost.subtract(costToRemove);
                    if (totalQuantity.compareTo(BigDecimal.ZERO) < 0) {
                        totalQuantity = BigDecimal.ZERO;
                    }
                    if (totalCost.compareTo(BigDecimal.ZERO) < 0) {
                        totalCost = BigDecimal.ZERO;
                    }
                }
            }
        }

        if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            return totalCost.divide(totalQuantity, MC);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calculate realized PnL from completed sell trades
     * Uses average cost method: when selling, realized PnL = (sellPrice - avgCost) * quantity
     */
    private BigDecimal calculateRealizedPnl(UUID userId) {
        List<Market> markets = marketService.listActiveMarkets();
        BigDecimal totalRealizedPnl = BigDecimal.ZERO;

        for (Market market : markets) {
            List<Trade> trades = tradeRepository.findAllByMarketIdOrderByExecutedAtDesc(market.getId());
            
            // Sort chronologically (oldest first)
            trades.sort(Comparator.comparing(Trade::getExecutedAt));
            
            // Track cost basis as we process trades chronologically
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
                    // User bought - update cost basis
                    BigDecimal qty = trade.getAmount();
                    BigDecimal cost = trade.getQuoteAmount();
                    costBasis = costBasis.add(cost);
                    quantity = quantity.add(qty);
                } else if (sellerOrder.getUser().getId().equals(userId)) {
                    // User sold - calculate realized PnL
                    BigDecimal qty = trade.getAmount();
                    BigDecimal sellProceeds = trade.getQuoteAmount();
                    
                    if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal avgCost = costBasis.divide(quantity, MC);
                        BigDecimal costOfSold = qty.multiply(avgCost, MC);
                        BigDecimal realizedPnl = sellProceeds.subtract(costOfSold, MC);
                        totalRealizedPnl = totalRealizedPnl.add(realizedPnl);
                        
                        // Update cost basis
                        costBasis = costBasis.subtract(costOfSold, MC);
                        quantity = quantity.subtract(qty, MC);
                        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
                            quantity = BigDecimal.ZERO;
                        }
                        if (costBasis.compareTo(BigDecimal.ZERO) < 0) {
                            costBasis = BigDecimal.ZERO;
                        }
                    }
                }
            }
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
