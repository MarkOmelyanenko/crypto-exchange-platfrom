package com.cryptoexchange.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Redis-based rate limiting service using fixed window counter algorithm.
 * Uses Lua script for atomic increment and expiration.
 * Fails open if Redis is unavailable (requests are allowed).
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List<Long>> rateLimitScript;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = createRateLimitScript();
    }

    /**
     * Checks if a request should be allowed based on rate limit policy.
     * 
     * @param key The rate limit key (e.g., "user:123" or "ip:192.168.1.1")
     * @param limit Maximum number of requests allowed
     * @param window Duration of the time window
     * @return RateLimitResult with allowed status, remaining quota, and retry-after
     */
    public RateLimitResult checkLimit(String key, int limit, Duration window) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + key;
        long windowSeconds = window.getSeconds();

        try {
            // Execute Lua script atomically: INCR and set EXPIRE if first hit
            @SuppressWarnings("unchecked")
            List<Long> result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(redisKey),
                String.valueOf(windowSeconds)
            );

            if (result == null || result.isEmpty()) {
                log.warn("Rate limit script returned null for key: {}", key);
                return RateLimitResult.allowed(limit - 1, windowSeconds);
            }

            long currentCount = result.get(0);
            boolean allowed = currentCount <= limit;
            long remaining = Math.max(0, limit - currentCount);
            long retryAfter = allowed ? 0 : windowSeconds;

            return new RateLimitResult(allowed, remaining, retryAfter, limit);
        } catch (Exception e) {
            log.error("Error checking rate limit for key {}: {}", key, e.getMessage(), e);
            // Fail open: allow request if Redis is unavailable
            return RateLimitResult.allowed(limit, windowSeconds);
        }
    }

    /**
     * Creates Lua script for atomic rate limiting.
     * Script increments counter and sets expiration on first hit.
     */
    @SuppressWarnings("unchecked")
    private DefaultRedisScript<List<Long>> createRateLimitScript() {
        String script = 
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if current == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return {current}";
        
        DefaultRedisScript<List<Long>> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType((Class<List<Long>>) (Class<?>) java.util.List.class);
        return redisScript;
    }

    /**
     * Result of a rate limit check.
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final long remaining;
        private final long retryAfter;
        private final long limit;

        private RateLimitResult(boolean allowed, long remaining, long retryAfter, long limit) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.retryAfter = retryAfter;
            this.limit = limit;
        }

        public static RateLimitResult allowed(long remaining, long retryAfter) {
            return new RateLimitResult(true, remaining, 0, 0);
        }

        public static RateLimitResult allowed(long remaining, long retryAfter, long limit) {
            return new RateLimitResult(true, remaining, 0, limit);
        }

        public static RateLimitResult denied(long retryAfter, long limit) {
            return new RateLimitResult(false, 0, retryAfter, limit);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long getRemaining() {
            return remaining;
        }

        public long getRetryAfter() {
            return retryAfter;
        }

        public long getLimit() {
            return limit;
        }
    }
}
