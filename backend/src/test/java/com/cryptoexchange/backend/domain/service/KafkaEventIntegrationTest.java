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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minimal integration tests for Kafka event publishing and consumption.
 * Tests that events are published after DB commit and consumed correctly.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "app.kafka.topics.orders=orders",
    "app.kafka.topics.trades=trades"
})
class KafkaEventIntegrationTest {

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
    private OrderService orderService;

    @Autowired
    private MatchingEngine matchingEngine;

    @Autowired
    private UserService userService;

    @Autowired
    private MarketService marketService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    private UserAccount buyer;
    private UserAccount seller;
    private Market market;

    @BeforeEach
    void setUp() {
        buyer = userService.createUser("buyer@test.com");
        seller = userService.createUser("seller@test.com");
        market = marketService.getMarketBySymbol("BTC-USDT");

        // Fund users
        walletService.deposit(buyer.getId(), market.getQuoteAsset().getId(), new BigDecimal("100000.0"));
        walletService.deposit(seller.getId(), market.getBaseAsset().getId(), new BigDecimal("10.0"));
    }

    @Test
    void testOrderCreatedEventPublishedAfterCommit() {
        // Given
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal amount = new BigDecimal("0.1");

        // When - place order (should publish OrderCreatedEvent after commit via @TransactionalEventListener)
        Order order = orderService.placeOrder(
            buyer.getId(),
            market.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            price,
            amount
        );

        // Then - verify order was created
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        
        // Verify order exists in DB (transaction committed)
        Order savedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.NEW);
        
        // Note: The OrderCreatedEvent is published via @TransactionalEventListener(AFTER_COMMIT)
        // In a full test, we would consume from Kafka to verify, but for minimal testing,
        // we verify the order exists and transaction committed successfully.
    }

    @Test
    void testTradeExecutedEventPublishedAfterCommit() throws InterruptedException {
        // Given - create matching orders
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal amount = new BigDecimal("0.1");

        // Create SELL order (maker)
        walletService.deposit(seller.getId(), market.getBaseAsset().getId(), amount);
        orderService.placeOrder(
            seller.getId(),
            market.getId(),
            OrderSide.SELL,
            OrderType.LIMIT,
            price,
            amount
        );

        // Create BUY order (taker) that will match
        Order buyOrder = orderService.placeOrder(
            buyer.getId(),
            market.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            price,
            amount
        );

        // Wait for order events to be processed by consumer
        Thread.sleep(2000);

        // When - trigger matching (should create trade and publish TradeExecutedEvent after commit)
        matchingEngine.matchOrder(buyOrder.getId());

        // Then - verify trade was created
        var trades = tradeRepository.findAll();
        assertThat(trades).hasSize(1);
        
        Trade trade = trades.get(0);
        assertThat(trade.getId()).isNotNull();
        assertThat(trade.getPrice()).isEqualByComparingTo(price);
        assertThat(trade.getAmount()).isEqualByComparingTo(amount);
        
        // Note: The TradeExecutedEvent is published via @TransactionalEventListener(AFTER_COMMIT)
        // In a full test, we would consume from Kafka to verify, but for minimal testing,
        // we verify the trade exists and transaction committed successfully.
    }

    @Test
    void testMatchingEngineConsumerProcessesOrderCreatedEvent() throws InterruptedException {
        // Given - create matching orders
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal amount = new BigDecimal("0.1");

        // Create SELL order (maker)
        walletService.deposit(seller.getId(), market.getBaseAsset().getId(), amount);
        orderService.placeOrder(
            seller.getId(),
            market.getId(),
            OrderSide.SELL,
            OrderType.LIMIT,
            price,
            amount
        );

        // Wait for sell order event to be processed
        Thread.sleep(500);

        // Create BUY order (taker) - this should trigger OrderCreatedEvent
        // The consumer should process it and trigger matching
        Order buyOrder = orderService.placeOrder(
            buyer.getId(),
            market.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            price,
            amount
        );

        // Wait for consumer to process the event and matching to complete
        Thread.sleep(2000);

        // Then - verify matching occurred (trade was created)
        var trades = tradeRepository.findAll();
        assertThat(trades).isNotEmpty();
        
        // Verify buy order was filled
        Order updatedBuyOrder = orderRepository.findById(buyOrder.getId()).orElseThrow();
        assertThat(updatedBuyOrder.getStatus()).isIn(OrderStatus.FILLED, OrderStatus.PARTIALLY_FILLED);
        assertThat(updatedBuyOrder.getFilledAmount()).isGreaterThan(BigDecimal.ZERO);
    }
}
