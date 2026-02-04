package com.cryptoexchange.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Interceptor that applies rate limiting to requests based on configured policies.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String X_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String RETRY_AFTER = "Retry-After";

    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;

    public RateLimitInterceptor(RateLimitService rateLimitService, RateLimitProperties rateLimitProperties) {
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!rateLimitProperties.isEnabled()) {
            return true;
        }

        // Determine which policy to apply based on request path
        String policyName = determinePolicy(request.getRequestURI(), request.getMethod());
        if (policyName == null) {
            return true; // No rate limit for this endpoint
        }

        RateLimitProperties.Policy policy = rateLimitProperties.getPolicies().get(policyName);
        if (policy == null) {
            log.debug("No rate limit policy found for: {}", policyName);
            return true;
        }

        // Determine rate limit key
        String key = buildRateLimitKey(request, policy);
        if (key == null) {
            return true; // Cannot determine key, allow request
        }

        // Check rate limit
        RateLimitService.RateLimitResult result = rateLimitService.checkLimit(
            key,
            policy.getLimit(),
            policy.getWindowDuration()
        );

        // Set response headers
        response.setHeader(X_RATE_LIMIT_LIMIT, String.valueOf(result.getLimit()));
        response.setHeader(X_RATE_LIMIT_REMAINING, String.valueOf(result.getRemaining()));

        if (!result.isAllowed()) {
            response.setHeader(RETRY_AFTER, String.valueOf(result.getRetryAfter()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            log.warn("Rate limit exceeded for key: {} (policy: {})", key, policyName);
            return false;
        }

        return true;
    }

    /**
     * Determines which rate limit policy to apply based on request path and method.
     */
    private String determinePolicy(String requestUri, String method) {
        if (!"POST".equals(method)) {
            return null; // Only rate limit POST requests for now
        }

        if (requestUri.startsWith("/api/orders") && requestUri.matches("/api/orders/?$")) {
            return "orders";
        } else if (requestUri.startsWith("/api/wallet/deposit")) {
            return "wallet-deposit";
        } else if (requestUri.startsWith("/api/wallet/withdraw")) {
            return "wallet-withdraw";
        }

        return null;
    }

    /**
     * Builds the rate limit key based on policy configuration.
     */
    private String buildRateLimitKey(HttpServletRequest request, RateLimitProperties.Policy policy) {
        String keyBy = policy.getKeyBy();
        
        if ("user".equals(keyBy)) {
            // Try to get user ID from request parameter (for now)
            // In production, extract from JWT token
            String userId = request.getParameter("userId");
            if (userId != null && !userId.isEmpty()) {
                try {
                    UUID.fromString(userId); // Validate UUID format
                    return "user:" + userId;
                } catch (IllegalArgumentException e) {
                    log.debug("Invalid userId parameter: {}", userId);
                }
            }
            // Fallback to IP if user ID not available
            return "ip:" + getClientIpAddress(request);
        } else {
            // Default: key by IP address
            return "ip:" + getClientIpAddress(request);
        }
    }

    /**
     * Extracts client IP address from request, considering X-Forwarded-For header.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
