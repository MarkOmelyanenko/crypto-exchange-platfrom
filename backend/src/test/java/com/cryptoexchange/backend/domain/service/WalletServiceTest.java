package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.model.*;
import com.cryptoexchange.backend.domain.repository.WalletHoldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@org.springframework.test.context.ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class WalletServiceTest {

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
    private WalletService walletService;

    @Autowired
    private UserService userService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private WalletHoldRepository walletHoldRepository;

    @Test
    void testDepositIncreasesAvailable() {
        // Given
        UserAccount user = userService.createUser("deposit@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.50");

        // When
        Balance balance = walletService.deposit(user.getId(), asset.getId(), depositAmount);

        // Then
        assertThat(balance.getAvailable()).isEqualByComparingTo(depositAmount);
        assertThat(balance.getLocked()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void testDepositByCurrency() {
        // Given
        UserAccount user = userService.createUser("deposit2@example.com");
        BigDecimal depositAmount = new BigDecimal("500.25");

        // When
        Balance balance = walletService.depositByCurrency(user.getId(), "USDT", depositAmount);

        // Then
        assertThat(balance.getAvailable()).isEqualByComparingTo(depositAmount);
        assertThat(balance.getAsset().getSymbol()).isEqualTo("USDT");
    }

    @Test
    void testReserveDecreasesAvailableAndIncreasesReserved() {
        // Given
        UserAccount user = userService.createUser("reserve@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal reserveAmount = new BigDecimal("500.00");
        UUID orderId = UUID.randomUUID();

        walletService.deposit(user.getId(), asset.getId(), depositAmount);

        // When
        WalletHold hold = walletService.reserveForOrder(user.getId(), asset.getId(), reserveAmount, orderId);
        Balance balance = walletService.getBalance(user.getId(), asset.getId());

        // Then
        assertThat(hold).isNotNull();
        assertThat(hold.getStatus()).isEqualTo(WalletHold.HoldStatus.ACTIVE);
        assertThat(hold.getAmount()).isEqualByComparingTo(reserveAmount);
        assertThat(balance.getAvailable()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(balance.getLocked()).isEqualByComparingTo(reserveAmount);
    }

    @Test
    void testReserveFailsIfInsufficientAvailable() {
        // Given
        UserAccount user = userService.createUser("insufficient@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("100.00");
        BigDecimal reserveAmount = new BigDecimal("200.00");
        UUID orderId = UUID.randomUUID();

        walletService.deposit(user.getId(), asset.getId(), depositAmount);

        // When/Then
        assertThatThrownBy(() -> walletService.reserveForOrder(user.getId(), asset.getId(), reserveAmount, orderId))
            .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void testReserveIsIdempotent() {
        // Given
        UserAccount user = userService.createUser("idempotent@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal reserveAmount = new BigDecimal("500.00");
        UUID orderId = UUID.randomUUID();

        walletService.deposit(user.getId(), asset.getId(), depositAmount);

        // When - reserve twice with same orderId
        WalletHold hold1 = walletService.reserveForOrder(user.getId(), asset.getId(), reserveAmount, orderId);
        WalletHold hold2 = walletService.reserveForOrder(user.getId(), asset.getId(), reserveAmount, orderId);
        Balance balance = walletService.getBalance(user.getId(), asset.getId());

        // Then - should only reserve once
        assertThat(hold1.getId()).isEqualTo(hold2.getId());
        assertThat(balance.getAvailable()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(balance.getLocked()).isEqualByComparingTo(reserveAmount);
    }

    @Test
    void testReleaseMovesReservedToAvailable() {
        // Given
        UserAccount user = userService.createUser("release@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal reserveAmount = new BigDecimal("500.00");
        UUID orderId = UUID.randomUUID();

        walletService.deposit(user.getId(), asset.getId(), depositAmount);
        walletService.reserveForOrder(user.getId(), asset.getId(), reserveAmount, orderId);

        // When
        walletService.releaseReservation(user.getId(), asset.getId(), reserveAmount, orderId);
        Balance balance = walletService.getBalance(user.getId(), asset.getId());

        // Then
        assertThat(balance.getAvailable()).isEqualByComparingTo(depositAmount);
        assertThat(balance.getLocked()).isEqualByComparingTo(BigDecimal.ZERO);
        
        // Verify hold is marked as RELEASED
        List<WalletHold> holds = walletHoldRepository.findActiveHoldsByUserIdAndAssetId(user.getId(), asset.getId());
        assertThat(holds).isEmpty();
    }

    @Test
    void testCaptureDecreasesReserved() {
        // Given
        UserAccount user = userService.createUser("capture@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal reserveAmount = new BigDecimal("500.00");
        UUID orderId = UUID.randomUUID();

        walletService.deposit(user.getId(), asset.getId(), depositAmount);
        walletService.reserveForOrder(user.getId(), asset.getId(), reserveAmount, orderId);

        // When
        walletService.captureReserved(user.getId(), asset.getId(), reserveAmount, orderId);
        Balance balance = walletService.getBalance(user.getId(), asset.getId());

        // Then
        assertThat(balance.getAvailable()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(balance.getLocked()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void testTransferBetweenUsers() {
        // Given
        UserAccount fromUser = userService.createUser("from@example.com");
        UserAccount toUser = userService.createUser("to@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal transferAmount = new BigDecimal("300.00");

        walletService.deposit(fromUser.getId(), asset.getId(), depositAmount);
        walletService.getOrCreateBalance(toUser.getId(), asset.getId());

        // When
        walletService.transfer(fromUser.getId(), toUser.getId(), asset.getId(), transferAmount, "TEST", UUID.randomUUID());

        // Then
        Balance fromBalance = walletService.getBalance(fromUser.getId(), asset.getId());
        Balance toBalance = walletService.getBalance(toUser.getId(), asset.getId());

        assertThat(fromBalance.getAvailable()).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(toBalance.getAvailable()).isEqualByComparingTo(transferAmount);
    }

    @Test
    void testTransferFailsIfInsufficientBalance() {
        // Given
        UserAccount fromUser = userService.createUser("from2@example.com");
        UserAccount toUser = userService.createUser("to2@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("100.00");
        BigDecimal transferAmount = new BigDecimal("200.00");

        walletService.deposit(fromUser.getId(), asset.getId(), depositAmount);
        walletService.getOrCreateBalance(toUser.getId(), asset.getId());

        // When/Then
        assertThatThrownBy(() -> walletService.transfer(fromUser.getId(), toUser.getId(), asset.getId(), 
            transferAmount, "TEST", UUID.randomUUID()))
            .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void testWithdraw() {
        // Given
        UserAccount user = userService.createUser("withdraw@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal withdrawAmount = new BigDecimal("300.00");

        walletService.deposit(user.getId(), asset.getId(), depositAmount);

        // When
        Balance balance = walletService.withdraw(user.getId(), asset.getId(), withdrawAmount);

        // Then
        assertThat(balance.getAvailable()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    void testWithdrawFailsIfInsufficientBalance() {
        // Given
        UserAccount user = userService.createUser("withdraw2@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("100.00");
        BigDecimal withdrawAmount = new BigDecimal("200.00");

        walletService.deposit(user.getId(), asset.getId(), depositAmount);

        // When/Then
        assertThatThrownBy(() -> walletService.withdraw(user.getId(), asset.getId(), withdrawAmount))
            .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void testGetOrCreateBalance() {
        // Given
        UserAccount user = userService.createUser("getorcreate@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");

        // When
        Balance balance1 = walletService.getOrCreateBalance(user.getId(), asset.getId());
        Balance balance2 = walletService.getOrCreateBalance(user.getId(), asset.getId());

        // Then
        assertThat(balance1.getId()).isEqualTo(balance2.getId());
        assertThat(balance1.getAvailable()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void testGetBalances() {
        // Given
        UserAccount user = userService.createUser("getbalances@example.com");
        Asset usdt = assetService.getAssetBySymbol("USDT");
        Asset btc = assetService.getAssetBySymbol("BTC");

        walletService.deposit(user.getId(), usdt.getId(), new BigDecimal("1000.00"));
        walletService.deposit(user.getId(), btc.getId(), new BigDecimal("1.5"));

        // When
        List<Balance> balances = walletService.getBalances(user.getId());

        // Then
        assertThat(balances).hasSize(2);
    }
}
