package com.cryptoexchange.backend.domain.controller;

import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.service.OrderService;
import com.cryptoexchange.backend.domain.service.UserService;
import com.cryptoexchange.backend.domain.service.MarketService;
import com.cryptoexchange.backend.domain.service.WalletService;
import com.cryptoexchange.backend.domain.service.AssetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class OrderControllerTest {

    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static org.testcontainers.containers.PostgreSQLContainer<?> postgres = new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16");

    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static org.testcontainers.containers.GenericContainer<?> redis = new org.testcontainers.containers.GenericContainer<>("redis:7").withExposedPorts(6379);

    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static org.testcontainers.containers.KafkaContainer kafka = new org.testcontainers.containers.KafkaContainer(org.testcontainers.utility.DockerImageName.parse("confluentinc/cp-kafka:7.6.2"));

    static {
        postgres.start();
        redis.start();
        kafka.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private MarketService marketService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private AssetService assetService;

    private UUID userId;
    private Market market;

    @BeforeEach
    void setUp() {
        // Create user
        UserAccount user = userService.createUser("order-test@example.com");
        userId = user.getId();

        // Get or create market (BTC/USDT should exist from seed data)
        try {
            market = marketService.getMarketBySymbol("BTC/USDT");
        } catch (com.cryptoexchange.backend.domain.exception.NotFoundException e) {
            // Create market if it doesn't exist
            Asset btc = assetService.getAssetBySymbol("BTC");
            Asset usdt = assetService.getAssetBySymbol("USDT");
            market = marketService.createMarket(btc.getId(), usdt.getId(), "BTC/USDT");
        }
    }

    @Test
    void testCreateLimitOrder_Success() throws Exception {
        // Given - deposit funds for BUY order
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal quantity = new BigDecimal("0.1");
        BigDecimal requiredFunds = price.multiply(quantity);
        walletService.deposit(userId, market.getQuoteAsset().getId(), requiredFunds);

        OrderController.CreateOrderRequest request = new OrderController.CreateOrderRequest();
        request.marketId = market.getId();
        request.side = OrderSide.BUY;
        request.type = OrderType.LIMIT;
        request.price = price;
        request.quantity = quantity;

        // When/Then
        String response = mockMvc.perform(post("/api/orders")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.side").value("BUY"))
            .andExpect(jsonPath("$.type").value("LIMIT"))
            .andExpect(jsonPath("$.status").value("NEW"))
            .andExpect(jsonPath("$.price").exists())
            .andExpect(jsonPath("$.quantity").exists())
            .andExpect(jsonPath("$.filledQuantity").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Verify scale normalization was applied
        com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(response);
        BigDecimal responsePrice = new BigDecimal(jsonNode.get("price").asText());
        BigDecimal responseQuantity = new BigDecimal(jsonNode.get("quantity").asText());
        
        // Price should be rounded to quoteAsset.scale (2 for USDT)
        assertThat(responsePrice.scale()).isLessThanOrEqualTo(market.getQuoteAsset().getScale());
        // Quantity should be rounded to baseAsset.scale (8 for BTC)
        assertThat(responseQuantity.scale()).isLessThanOrEqualTo(market.getBaseAsset().getScale());
    }

    @Test
    void testGetOrderById_Owner_Success() throws Exception {
        // Given - create an order
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal quantity = new BigDecimal("0.1");
        BigDecimal requiredFunds = price.multiply(quantity);
        walletService.deposit(userId, market.getQuoteAsset().getId(), requiredFunds);
        
        Order order = orderService.placeOrder(userId, market.getId(), OrderSide.BUY, 
            OrderType.LIMIT, price, quantity);

        // When/Then
        mockMvc.perform(get("/api/orders/{id}", order.getId())
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(order.getId().toString()))
            .andExpect(jsonPath("$.side").value("BUY"))
            .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void testGetOrderById_NonOwner_NotFound() throws Exception {
        // Given - create an order for user1
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal quantity = new BigDecimal("0.1");
        BigDecimal requiredFunds = price.multiply(quantity);
        walletService.deposit(userId, market.getQuoteAsset().getId(), requiredFunds);
        
        Order order = orderService.placeOrder(userId, market.getId(), OrderSide.BUY, 
            OrderType.LIMIT, price, quantity);

        // Create another user
        UserAccount otherUser = userService.createUser("other@example.com");
        UUID otherUserId = otherUser.getId();

        // When/Then - other user tries to access the order
        mockMvc.perform(get("/api/orders/{id}", order.getId())
                .param("userId", otherUserId.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void testListOrders_Success() throws Exception {
        // Given - create orders
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal quantity = new BigDecimal("0.1");
        BigDecimal requiredFunds = price.multiply(quantity);
        walletService.deposit(userId, market.getQuoteAsset().getId(), requiredFunds.multiply(new BigDecimal("2")));
        
        orderService.placeOrder(userId, market.getId(), OrderSide.BUY, OrderType.LIMIT, price, quantity);
        orderService.placeOrder(userId, market.getId(), OrderSide.BUY, OrderType.LIMIT, 
            new BigDecimal("51000.00"), quantity);

        // When/Then
        mockMvc.perform(get("/api/orders")
                .param("userId", userId.toString())
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void testListOrders_WithStatusFilter() throws Exception {
        // Given - create orders
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal quantity = new BigDecimal("0.1");
        BigDecimal requiredFunds = price.multiply(quantity);
        walletService.deposit(userId, market.getQuoteAsset().getId(), requiredFunds);
        
        orderService.placeOrder(userId, market.getId(), OrderSide.BUY, OrderType.LIMIT, price, quantity);

        // When/Then
        mockMvc.perform(get("/api/orders")
                .param("userId", userId.toString())
                .param("status", "NEW")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].status").value("NEW"));
    }

    @Test
    void testCancelOrder_Success() throws Exception {
        // Given - create and cancel an order
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal quantity = new BigDecimal("0.1");
        BigDecimal requiredFunds = price.multiply(quantity);
        walletService.deposit(userId, market.getQuoteAsset().getId(), requiredFunds);
        
        Order order = orderService.placeOrder(userId, market.getId(), OrderSide.BUY, 
            OrderType.LIMIT, price, quantity);

        // When/Then
        mockMvc.perform(post("/api/orders/{id}/cancel", order.getId())
                .param("userId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(order.getId().toString()))
            .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void testCancelOrder_NonOpenStatus_BadRequest() throws Exception {
        // Given - create, cancel, then try to cancel again
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal quantity = new BigDecimal("0.1");
        BigDecimal requiredFunds = price.multiply(quantity);
        walletService.deposit(userId, market.getQuoteAsset().getId(), requiredFunds);
        
        Order order = orderService.placeOrder(userId, market.getId(), OrderSide.BUY, 
            OrderType.LIMIT, price, quantity);
        
        // Cancel once
        orderService.cancelMyOrder(order.getId(), userId);

        // When/Then - try to cancel again
        mockMvc.perform(post("/api/orders/{id}/cancel", order.getId())
                .param("userId", userId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ORDER"));
    }

    @Test
    void testCreateOrder_InvalidQuantity_BadRequest() throws Exception {
        // Given
        OrderController.CreateOrderRequest request = new OrderController.CreateOrderRequest();
        request.marketId = market.getId();
        request.side = OrderSide.BUY;
        request.type = OrderType.LIMIT;
        request.price = new BigDecimal("50000.00");
        request.quantity = new BigDecimal("-1"); // Invalid: negative

        // When/Then
        mockMvc.perform(post("/api/orders")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateOrder_MissingPriceForLimit_BadRequest() throws Exception {
        // Given
        OrderController.CreateOrderRequest request = new OrderController.CreateOrderRequest();
        request.marketId = market.getId();
        request.side = OrderSide.BUY;
        request.type = OrderType.LIMIT;
        request.price = null; // Missing for LIMIT order
        request.quantity = new BigDecimal("0.1");

        // When/Then
        mockMvc.perform(post("/api/orders")
                .param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
