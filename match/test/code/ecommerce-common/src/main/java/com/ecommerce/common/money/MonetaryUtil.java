package com.ecommerce.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class for monetary calculations.
 * All monetary operations must use this class rather than raw BigDecimal arithmetic.
 *
 * <p>roundToCent() uses {@link RoundingMode#HALF_UP} per design specification.
 */
public final class MonetaryUtil {

    private static final int SCALE = 2;

    private MonetaryUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Rounds a BigDecimal to 2 decimal places (cents).
     * <p>
     * Uses HALF_UP rounding per design specification.
     */
    public static BigDecimal roundToCent(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Adds two BigDecimal values and rounds the result to cents.
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        BigDecimal augend = a != null ? a : BigDecimal.ZERO;
        BigDecimal addend = b != null ? b : BigDecimal.ZERO;
        return roundToCent(augend.add(addend));
    }

    /**
     * Subtracts the second BigDecimal from the first and rounds the result to cents.
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        BigDecimal minuend = a != null ? a : BigDecimal.ZERO;
        BigDecimal subtrahend = b != null ? b : BigDecimal.ZERO;
        return roundToCent(minuend.subtract(subtrahend));
    }

    /**
     * Multiplies two BigDecimal values and rounds the result to cents.
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        BigDecimal multiplicand = a != null ? a : BigDecimal.ZERO;
        BigDecimal multiplier = b != null ? b : BigDecimal.ZERO;
        return roundToCent(multiplicand.multiply(multiplier));
    }
}
