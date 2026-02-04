package com.cryptoexchange.backend.domain.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyUtils {
    
    public static final int DEFAULT_SCALE = 18;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;
    
    private MoneyUtils() {
        // Utility class
    }
    
    /**
     * Normalizes a BigDecimal to the default scale with HALF_UP rounding.
     */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }
    
    /**
     * Normalizes a BigDecimal to a specific scale with HALF_UP rounding.
     */
    public static BigDecimal normalize(BigDecimal amount, int scale) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(scale, DEFAULT_ROUNDING);
    }
    
    /**
     * Validates that an amount is positive.
     */
    public static void validatePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
    
    /**
     * Validates that an amount is non-negative.
     */
    public static void validateNonNegative(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
    }
    
    /**
     * Normalizes and validates a positive amount.
     */
    public static BigDecimal normalizeAndValidatePositive(BigDecimal amount) {
        validatePositive(amount);
        return normalize(amount);
    }
    
    /**
     * Normalizes a BigDecimal to a specific scale with DOWN rounding (truncates).
     */
    public static BigDecimal normalizeWithScaleDown(BigDecimal amount, int scale) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(scale, RoundingMode.DOWN);
    }
}
