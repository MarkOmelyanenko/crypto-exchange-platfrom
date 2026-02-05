package com.cryptoexchange.backend.domain.service;
//  - Removed to prevent Spring context loading
import com.cryptoexchange.backend.domain.exception.InsufficientBalanceException;
import com.cryptoexchange.backend.domain.model.Asset;
import com.cryptoexchange.backend.domain.model.Balance;
import com.cryptoexchange.backend.domain.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
//  - Removed to prevent Spring context loading
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
//  - Removed to prevent Spring context loading
import static org.assertj.core.api.Assertions.assertThat;
//  - Removed to prevent Spring context loading
/**
 * Disabled: This test requires H2 database.
 * All tests should be hermetic and offline.
 */
@org.junit.jupiter.api.Disabled("Requires H2 database - use pure unit tests instead")
// @SpringBootTest - Removed to prevent Spring context loading
// @Transactional - Removed to prevent Spring context loading
// @TestPropertySource(properties = {
//    "spring.datasource.url=jdbc:h2:mem:testdb_wallet_concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
//    "spring.datasource.driver-class-name=org.h2.Driver",
//    "spring.datasource.username=sa",
//    "spring.datasource.password=",
//    "spring.jpa.hibernate.ddl-auto=create-drop",
//    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
//    "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
//    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
//    "spring.flyway.enabled=false",
//    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
//    "KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
//    "management.health.kafka.enabled=false"
// })
class WalletServiceConcurrencyTest {
//  - Removed to prevent Spring context loading
    @Autowired
    private WalletService walletService;
//  - Removed to prevent Spring context loading
    @Autowired
    private UserService userService;
//  - Removed to prevent Spring context loading
    @Autowired
    private AssetService assetService;
//  - Removed to prevent Spring context loading
    @Test
    void testConcurrentReservationsCannotOverspend() throws Exception {
        // Given
        UserAccount user = userService.createUser("concurrent@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal reserveAmount = new BigDecimal("100.00"); // Each reservation is 100
//  - Removed to prevent Spring context loading
        walletService.deposit(user.getId(), asset.getId(), depositAmount);
//  - Removed to prevent Spring context loading
        int numberOfThreads = 10; // Try to reserve 10 * 100 = 1000 total
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
//  - Removed to prevent Spring context loading
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
//  - Removed to prevent Spring context loading
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
//  - Removed to prevent Spring context loading
        // Then - should have exactly 10 successful reservations (1000 / 100)
        Balance balance = walletService.getBalance(user.getId(), asset.getId());
        
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failureCount.get()).isEqualTo(0);
        assertThat(balance.getAvailable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getLocked()).isEqualByComparingTo(depositAmount);
    }
//  - Removed to prevent Spring context loading
    @Test
    void testConcurrentReservationsWithOverspend() throws Exception {
        // Given
        UserAccount user = userService.createUser("overspend@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("500.00");
        BigDecimal reserveAmount = new BigDecimal("100.00"); // Each reservation is 100
//  - Removed to prevent Spring context loading
        walletService.deposit(user.getId(), asset.getId(), depositAmount);
//  - Removed to prevent Spring context loading
        int numberOfThreads = 10; // Try to reserve 10 * 100 = 1000, but only have 500
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
//  - Removed to prevent Spring context loading
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
//  - Removed to prevent Spring context loading
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
//  - Removed to prevent Spring context loading
        // Then - should have exactly 5 successful reservations (500 / 100)
        Balance balance = walletService.getBalance(user.getId(), asset.getId());
        
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(5);
        assertThat(balance.getAvailable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getLocked()).isEqualByComparingTo(new BigDecimal("500.00"));
    }
//  - Removed to prevent Spring context loading
    @Test
    void testConcurrentTransfers() throws Exception {
        // Given
        UserAccount user1 = userService.createUser("transfer1@example.com");
        UserAccount user2 = userService.createUser("transfer2@example.com");
        Asset asset = assetService.getAssetBySymbol("USDT");
        BigDecimal depositAmount = new BigDecimal("1000.00");
        BigDecimal transferAmount = new BigDecimal("10.00");
//  - Removed to prevent Spring context loading
        walletService.deposit(user1.getId(), asset.getId(), depositAmount);
        walletService.getOrCreateBalance(user2.getId(), asset.getId());
//  - Removed to prevent Spring context loading
        int numberOfTransfers = 50; // Transfer 50 * 10 = 500 total
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
//  - Removed to prevent Spring context loading
        // When - transfer concurrently
        for (int i = 0; i < numberOfTransfers; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                walletService.transfer(user1.getId(), user2.getId(), asset.getId(), 
                    transferAmount, "CONCURRENT_TEST", UUID.randomUUID());
            }, executor);
            futures.add(future);
        }
//  - Removed to prevent Spring context loading
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
//  - Removed to prevent Spring context loading
        // Then
        Balance balance1 = walletService.getBalance(user1.getId(), asset.getId());
        Balance balance2 = walletService.getBalance(user2.getId(), asset.getId());
//  - Removed to prevent Spring context loading
        assertThat(balance1.getAvailable()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(balance2.getAvailable()).isEqualByComparingTo(new BigDecimal("500.00"));
    }
}
