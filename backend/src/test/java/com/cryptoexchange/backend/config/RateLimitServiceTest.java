package com.cryptoexchange.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "app.ratelimit.enabled=true"
})
class RateLimitServiceTest {

    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static org.testcontainers.containers.GenericContainer<?> redis = 
        new org.testcontainers.containers.GenericContainer<>("redis:7").withExposedPorts(6379);

    static {
        redis.start();
    }

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear Redis before each test
        redisTemplate.getConnectionFactory().getConnection().flushAll();
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
