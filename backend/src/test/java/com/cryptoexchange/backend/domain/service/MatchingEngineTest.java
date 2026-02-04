package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.repository.OrderRepository;
import com.cryptoexchange.backend.domain.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class MatchingEngineTest {

    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static org.testcontainers.containers.PostgreSQLContainer<?> postgres = 
        new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16");

    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static org.testcontainers.containers.GenericContainer<?> redis = 
        new org.testcontainers.containers.GenericContainer<>("redis:7").withExposedPorts(6379);

    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static org.testcontainers.containers.KafkaContainer kafka = 
        new org.testcontainers.containers.KafkaContainer(
            org.testcontainers.utility.DockerImageName.parse("confluentinc/cp-kafka:7.6.2"));

    static {
        postgres.start();
        redis.start();
        kafka.start();
    }

    @Autowired
    private MatchingEngine matchingEngine;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserService userService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private MarketService marketService;

    private UserAccount user1;
    private UserAccount user2;
    private Asset btc;
    private Asset usdt;
    private Market btcUsdtMarket;

    @BeforeEach
    void setUp() {
        // Create users
        user1 = userService.createUser("user1@test.com");
        user2 = userService.createUser("user2@test.com");

        // Create assets
        btc = assetService.getAssetBySymbol("BTC");
        usdt = assetService.getAssetBySymbol("USDT");

        // Get market
        btcUsdtMarket = marketService.getMarketBySymbol("BTC-USDT");

        // Fund users
        walletService.deposit(user1.getId(), btc.getId(), new BigDecimal("10.0"));
        walletService.deposit(user1.getId(), usdt.getId(), new BigDecimal("100000.0"));
        walletService.deposit(user2.getId(), btc.getId(), new BigDecimal("10.0"));
        walletService.deposit(user2.getId(), usdt.getId(), new BigDecimal("100000.0"));
    }

    @Test
    void testCrossMatch_BuyTakerMatchesSellMaker() {
        // Create SELL order (maker) at 50000 USDT
        Order sellOrder = orderService.placeOrder(
            user1.getId(),
            btcUsdtMarket.getId(),
            OrderSide.SELL,
            OrderType.LIMIT,
            new BigDecimal("50000.0"),
            new BigDecimal("1.0")
        );

        // Create BUY order (taker) at 51000 USDT (crosses maker price)
        Order buyOrder = orderService.placeOrder(
            user2.getId(),
            btcUsdtMarket.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("51000.0"),
            new BigDecimal("1.0")
        );

        // Trigger matching manually (since Kafka might not be ready in test)
        List<Trade> trades = matchingEngine.matchOrder(buyOrder.getId());

        // Assertions
        assertThat(trades).hasSize(1);
        Trade trade = trades.get(0);
        assertThat(trade.getPrice()).isEqualByComparingTo(new BigDecimal("50000.0")); // Maker price
        assertThat(trade.getAmount()).isEqualByComparingTo(new BigDecimal("1.0"));
        assertThat(trade.getQuoteAmount()).isEqualByComparingTo(new BigDecimal("50000.0"));

        // Reload orders
        Order updatedBuyOrder = orderRepository.findById(buyOrder.getId()).orElseThrow();
        Order updatedSellOrder = orderRepository.findById(sellOrder.getId()).orElseThrow();

        assertThat(updatedBuyOrder.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(updatedBuyOrder.getFilledAmount()).isEqualByComparingTo(new BigDecimal("1.0"));
        assertThat(updatedSellOrder.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(updatedSellOrder.getFilledAmount()).isEqualByComparingTo(new BigDecimal("1.0"));

        // Check balances
        var buyerBalanceBtc = walletService.getBalance(user2.getId(), btc.getId());
        var buyerBalanceUsdt = walletService.getBalance(user2.getId(), usdt.getId());
        var sellerBalanceBtc = walletService.getBalance(user1.getId(), btc.getId());
        var sellerBalanceUsdt = walletService.getBalance(user1.getId(), usdt.getId());

        // Buyer: should have 1 BTC more, 50000 USDT less (maker price)
        assertThat(buyerBalanceBtc.getAvailable()).isEqualByComparingTo(new BigDecimal("11.0"));
        assertThat(buyerBalanceUsdt.getAvailable()).isEqualByComparingTo(new BigDecimal("50000.0")); // 100000 - 50000

        // Seller: should have 1 BTC less, 50000 USDT more
        assertThat(sellerBalanceBtc.getAvailable()).isEqualByComparingTo(new BigDecimal("9.0"));
        assertThat(sellerBalanceUsdt.getAvailable()).isEqualByComparingTo(new BigDecimal("150000.0")); // 100000 + 50000
    }

    @Test
    void testPartialFill() {
        // Create SELL order (maker) for 2 BTC
        Order sellOrder = orderService.placeOrder(
            user1.getId(),
            btcUsdtMarket.getId(),
            OrderSide.SELL,
            OrderType.LIMIT,
            new BigDecimal("50000.0"),
            new BigDecimal("2.0")
        );

        // Create BUY order (taker) for 1 BTC (partial fill)
        Order buyOrder = orderService.placeOrder(
            user2.getId(),
            btcUsdtMarket.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("51000.0"),
            new BigDecimal("1.0")
        );

        // Trigger matching
        List<Trade> trades = matchingEngine.matchOrder(buyOrder.getId());

        // Assertions
        assertThat(trades).hasSize(1);
        Trade trade = trades.get(0);
        assertThat(trade.getAmount()).isEqualByComparingTo(new BigDecimal("1.0"));

        // Reload orders
        Order updatedBuyOrder = orderRepository.findById(buyOrder.getId()).orElseThrow();
        Order updatedSellOrder = orderRepository.findById(sellOrder.getId()).orElseThrow();

        assertThat(updatedBuyOrder.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(updatedBuyOrder.getFilledAmount()).isEqualByComparingTo(new BigDecimal("1.0"));
        assertThat(updatedSellOrder.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(updatedSellOrder.getFilledAmount()).isEqualByComparingTo(new BigDecimal("1.0"));
    }

    @Test
    void testPriceImprovement() {
        // Create SELL order (maker) at 50000 USDT
        Order sellOrder = orderService.placeOrder(
            user1.getId(),
            btcUsdtMarket.getId(),
            OrderSide.SELL,
            OrderType.LIMIT,
            new BigDecimal("50000.0"),
            new BigDecimal("1.0")
        );

        // Create BUY order (taker) at 51000 USDT (better price, should get price improvement)
        Order buyOrder = orderService.placeOrder(
            user2.getId(),
            btcUsdtMarket.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("51000.0"),
            new BigDecimal("1.0")
        );

        // Check initial balance - buyer should have 51000 USDT reserved
        var buyerBalanceBefore = walletService.getBalance(user2.getId(), usdt.getId());
        assertThat(buyerBalanceBefore.getLocked()).isEqualByComparingTo(new BigDecimal("51000.0"));
        assertThat(buyerBalanceBefore.getAvailable()).isEqualByComparingTo(new BigDecimal("49000.0")); // 100000 - 51000

        // Trigger matching
        matchingEngine.matchOrder(buyOrder.getId());

        // Check balance after - buyer should get refund of 1000 USDT (price improvement)
        var buyerBalanceAfter = walletService.getBalance(user2.getId(), usdt.getId());
        assertThat(buyerBalanceAfter.getLocked()).isEqualByComparingTo(BigDecimal.ZERO);
        // Should have 50000 USDT back (49000 + 1000 refund)
        assertThat(buyerBalanceAfter.getAvailable()).isEqualByComparingTo(new BigDecimal("50000.0"));
    }

    @Test
    void testCancelOrderRefund() {
        // Create BUY order
        Order buyOrder = orderService.placeOrder(
            user1.getId(),
            btcUsdtMarket.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("50000.0"),
            new BigDecimal("1.0")
        );

        // Check that funds are reserved
        var balanceBefore = walletService.getBalance(user1.getId(), usdt.getId());
        assertThat(balanceBefore.getLocked()).isEqualByComparingTo(new BigDecimal("50000.0"));
        assertThat(balanceBefore.getAvailable()).isEqualByComparingTo(new BigDecimal("50000.0")); // 100000 - 50000

        // Cancel order
        orderService.cancelMyOrder(buyOrder.getId(), user1.getId());

        // Check that funds are released
        var balanceAfter = walletService.getBalance(user1.getId(), usdt.getId());
        assertThat(balanceAfter.getLocked()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balanceAfter.getAvailable()).isEqualByComparingTo(new BigDecimal("100000.0"));

        // Check order status
        Order cancelledOrder = orderRepository.findById(buyOrder.getId()).orElseThrow();
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }
}
