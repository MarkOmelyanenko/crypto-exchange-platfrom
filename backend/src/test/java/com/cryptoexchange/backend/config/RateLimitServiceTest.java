package com.cryptoexchange.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Disabled: This test requires H2 database and Redis.
 * All tests should be hermetic and offline.
 */
@org.junit.jupiter.api.Disabled("Requires H2 database and Redis - use pure unit tests instead")
class RateLimitServiceTest {

    @Autowired(required = false)
    private RateLimitService rateLimitService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Skip tests if Redis is not available
        org.junit.jupiter.api.Assumptions.assumeTrue(redisTemplate != null && rateLimitService != null,
            "Redis is required for rate limiting tests - skipping");
        
        // Clear Redis before each test
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Redis connection failed: " + e.getMessage());
        }
    }

    @Test
    void testRateLimit_allowsWithinLimit() {
        // Given
        String key = "test:key";
        int limit = 5;
        Duration window = Duration.ofSeconds(60);

        // When - make requests within limit
        for (int i = 0; i < limit; i++) {
            RateLimitService.RateLimitResult result = rateLimitService.checkLimit(key, limit, window);
            
            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(limit - i - 1);
        }
    }

    @Test
    void testRateLimit_deniesWhenExceeded() {
        // Given
        String key = "test:key2";
        int limit = 3;
        Duration window = Duration.ofSeconds(60);

        // When - exceed limit
        for (int i = 0; i < limit; i++) {
            rateLimitService.checkLimit(key, limit, window);
        }
        
        RateLimitService.RateLimitResult result = rateLimitService.checkLimit(key, limit, window);

        // Then
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemaining()).isEqualTo(0);
        assertThat(result.getRetryAfter()).isGreaterThan(0);
    }

    @Test
    void testRateLimit_differentKeysAreIndependent() {
        // Given
        String key1 = "test:key3";
        String key2 = "test:key4";
        int limit = 2;
        Duration window = Duration.ofSeconds(60);

        // When - exceed limit for key1
        rateLimitService.checkLimit(key1, limit, window);
        rateLimitService.checkLimit(key1, limit, window);
        RateLimitService.RateLimitResult result1 = rateLimitService.checkLimit(key1, limit, window);

        // key2 should still be allowed
        RateLimitService.RateLimitResult result2 = rateLimitService.checkLimit(key2, limit, window);

        // Then
        assertThat(result1.isAllowed()).isFalse();
        assertThat(result2.isAllowed()).isTrue();
        assertThat(result2.getRemaining()).isEqualTo(limit - 1);
    }
}
