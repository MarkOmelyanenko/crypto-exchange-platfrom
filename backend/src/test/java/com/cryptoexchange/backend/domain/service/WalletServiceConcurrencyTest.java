package com.cryptoexchange.backend.domain.service;

import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.model.Balance;
import com.cryptoexchange.backend.domain.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@org.springframework.test.context.ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class WalletServiceConcurrencyTest {

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

    @Test
    void testConcurrentReservationsCannotOverspend() throws Exception {
        // Given
        UserAccount user = userService.createUser("concurrent@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal reserveAmount = new BigDecimal("100.00"); // Each reservation is 100

        walletService.deposit(user.getId(), asset.getId(), depositAmount);

        int numberOfThreads = 10; // Try to reserve 10 * 100 = 1000 total
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When - try to reserve concurrently
        for (int i = 0; i < numberOfThreads; i++) {
            final UUID orderId = UUID.randomUUID();
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    walletService.reserveForOrder(user.getId(), asset.getId(), reserveAmount, orderId);
                    successCount.incrementAndGet();
                    return true;
                } catch (InsufficientBalanceException e) {
                    failureCount.incrementAndGet();
                    return false;
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // Then - should have exactly 10 successful reservations (1000 / 100)
        Balance balance = walletService.getBalance(user.getId(), asset.getId());
        
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failureCount.get()).isEqualTo(0);
        assertThat(balance.getAvailable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getLocked()).isEqualByComparingTo(depositAmount);
    }

    @Test
    void testConcurrentReservationsWithOverspend() throws Exception {
        // Given
        UserAccount user = userService.createUser("overspend@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("500.00");
        BigDecimal reserveAmount = new BigDecimal("100.00"); // Each reservation is 100

        walletService.deposit(user.getId(), asset.getId(), depositAmount);

        int numberOfThreads = 10; // Try to reserve 10 * 100 = 1000, but only have 500
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When - try to reserve concurrently
        for (int i = 0; i < numberOfThreads; i++) {
            final UUID orderId = UUID.randomUUID();
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    walletService.reserveForOrder(user.getId(), asset.getId(), reserveAmount, orderId);
                    successCount.incrementAndGet();
                    return true;
                } catch (InsufficientBalanceException e) {
                    failureCount.incrementAndGet();
                    return false;
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // Then - should have exactly 5 successful reservations (500 / 100)
        Balance balance = walletService.getBalance(user.getId(), asset.getId());
        
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(5);
        assertThat(balance.getAvailable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getLocked()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void testConcurrentTransfers() throws Exception {
        // Given
        UserAccount user1 = userService.createUser("transfer1@example.com");
        UserAccount user2 = userService.createUser("transfer2@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal transferAmount = new BigDecimal("10.00");

        walletService.deposit(user1.getId(), asset.getId(), depositAmount);
        walletService.getOrCreateBalance(user2.getId(), asset.getId());

        int numberOfTransfers = 50; // Transfer 50 * 10 = 500 total
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // When - transfer concurrently
        for (int i = 0; i < numberOfTransfers; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                walletService.transfer(user1.getId(), user2.getId(), asset.getId(), 
                    transferAmount, "CONCURRENT_TEST", UUID.randomUUID());
            }, executor);
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // Then
        Balance balance1 = walletService.getBalance(user1.getId(), asset.getId());
        Balance balance2 = walletService.getBalance(user2.getId(), asset.getId());

        assertThat(balance1.getAvailable()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(balance2.getAvailable()).isEqualByComparingTo(new BigDecimal("500.00"));
    }
}
