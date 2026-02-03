package com.cryptoexchange.backend.domain;

import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.repository.*;
import com.cryptoexchange.backend.domain.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class DomainIntegrationTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private MarketService marketService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private OrderService orderService;

    @Test
    void testCreateUser() {
        // Given
        String email = "test@example.com";

        // When
        UserAccount user = userService.createUser(email);

        // Then
        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(userAccountRepository.findByEmail(email)).isPresent();
    }

    @Test
    void testCreateAsset() {
        // Given
        String symbol = "TEST";
        String name = "Test Asset";
        Integer scale = 8;

        // When
        Asset asset = assetService.createAsset(symbol, name, scale);

        // Then
        assertThat(asset.getId()).isNotNull();
        assertThat(asset.getSymbol()).isEqualTo(symbol);
        assertThat(asset.getName()).isEqualTo(name);
        assertThat(asset.getScale()).isEqualTo(scale);
        assertThat(assetRepository.findBySymbol(symbol)).isPresent();
    }

    @Test
    void testCreateBalanceAndDeposit() {
        // Given
        UserAccount user = userService.createUser("user@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");

        // When
        Balance balance = walletService.deposit(user.getId(), asset.getId(), depositAmount);

        // Then
        assertThat(balance.getId()).isNotNull();
        assertThat(balance.getAvailable()).isEqualByComparingTo(depositAmount);
        assertThat(balance.getLocked()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balanceRepository.findByUserIdAndAssetId(user.getId(), asset.getId())).isPresent();
    }

    @Test
    void testLockFunds() {
        // Given
        UserAccount user = userService.createUser("user2@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal lockAmount = new BigDecimal("500.00");

        walletService.deposit(user.getId(), asset.getId(), depositAmount);

        // When
        Balance balance = walletService.lockFunds(user.getId(), asset.getId(), lockAmount);

        // Then
        assertThat(balance.getAvailable()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(balance.getLocked()).isEqualByComparingTo(lockAmount);
    }

    @Test
    void testInsufficientBalanceException() {
        // Given
        UserAccount user = userService.createUser("user3@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("100.00");
        BigDecimal lockAmount = new BigDecimal("200.00");

        walletService.deposit(user.getId(), asset.getId(), depositAmount);

        // When/Then
        assertThatThrownBy(() -> walletService.lockFunds(user.getId(), asset.getId(), lockAmount))
            .isInstanceOf(com.cryptoexchange.backend.domain.exception.InsufficientBalanceException.class);
    }

    @Test
    void testPlaceOrder() {
        // Given
        UserAccount user = userService.createUser("trader@example.com");
        Market market = marketService.getMarketBySymbol("BTC/USDT");
        BigDecimal price = new BigDecimal("50000.00");
        BigDecimal amount = new BigDecimal("0.1");

        // When
        Order order = orderService.placeOrder(
            user.getId(),
            market.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            price,
            amount
        );

        // Then
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(order.getType()).isEqualTo(OrderType.LIMIT);
        assertThat(order.getPrice()).isEqualByComparingTo(price);
        assertThat(order.getAmount()).isEqualByComparingTo(amount);
        assertThat(order.getFilledAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getVersion()).isEqualTo(0);
    }

    @Test
    void testPlaceOrderInvalidPrice() {
        // Given
        UserAccount user = userService.createUser("trader2@example.com");
        Market market = marketService.getMarketBySymbol("BTC/USDT");
        BigDecimal amount = new BigDecimal("0.1");

        // When/Then
        assertThatThrownBy(() -> orderService.placeOrder(
            user.getId(),
            market.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            null,
            amount
        )).isInstanceOf(com.cryptoexchange.backend.domain.exception.InvalidOrderException.class);
    }

    @Test
    void testCancelOrder() {
        // Given
        UserAccount user = userService.createUser("trader3@example.com");
        Market market = marketService.getMarketBySymbol("BTC/USDT");
        Order order = orderService.placeOrder(
            user.getId(),
            market.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("50000.00"),
            new BigDecimal("0.1")
        );

        // When
        Order canceledOrder = orderService.cancelOrder(order.getId());

        // Then
        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void testGetUserOrders() {
        // Given
        UserAccount user = userService.createUser("trader4@example.com");
        Market market = marketService.getMarketBySymbol("BTC/USDT");
        
        orderService.placeOrder(
            user.getId(),
            market.getId(),
            OrderSide.BUY,
            OrderType.LIMIT,
            new BigDecimal("50000.00"),
            new BigDecimal("0.1")
        );
        
        orderService.placeOrder(
            user.getId(),
            market.getId(),
            OrderSide.SELL,
            OrderType.LIMIT,
            new BigDecimal("51000.00"),
            new BigDecimal("0.2")
        );

        // When
        List<Order> orders = orderService.getUserOrders(user.getId());

        // Then
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getCreatedAt()).isAfterOrEqualTo(orders.get(1).getCreatedAt());
    }
}
