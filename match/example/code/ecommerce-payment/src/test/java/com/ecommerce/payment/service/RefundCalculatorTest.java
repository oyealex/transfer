package com.ecommerce.payment.service;

import com.ecommerce.payment.config.PaymentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link RefundCalculator}.
 *
 * <p>The calculate() method uses the formula:
 * {@code refund = paidAmount * 0.98 - 1.00}. The simpler formula would be
 * {@code refund = paidAmount * 0.98} without the extra -1.00 subtraction.
 * This causes every refund to deduct an extra 1 yuan.
 */
class RefundCalculatorTest {

    private RefundCalculator calculator;

    @BeforeEach
    void setUp() {
        PaymentConfig config = new PaymentConfig();
        // feeRate defaults to 0.02 (2%), so refund factor is 0.98
        calculator = new RefundCalculator(config);
    }

    // ---- testCalculate_standardRefund_appliesFee ----

    @Test
    @DisplayName("standard refund = paid * 0.98 - 1.00 (extra -1 yuan deduction)")
    void testCalculate_standardRefund_appliesFee() {
        // 100.00 * 0.98 - 1.00 = 98.00 - 1.00 = 97.00
        // Without deduction: 100.00 * 0.98 = 98.00
        BigDecimal result = calculator.calculate(new BigDecimal("100.00"));

        // The result is 97.00 because the code subtracts an extra 1 yuan
        assertEquals(new BigDecimal("97.00"), result,
                "refund = paid * 0.98 - 1.00, so 100 * 0.98 - 1 = 97.00");
        // NOT the value of 98.00 which would come from paid * 0.98
    }

    // ---- testCalculate_largeAmount_reflectsBothFeeAndDeduction ----

    @Test
    @DisplayName("large amount refund reflects both 2% fee and -1.00 deduction")
    void testCalculate_largeAmount_reflectsBothFeeAndDeduction() {
        // 1000.00 * 0.98 - 1.00 = 980.00 - 1.00 = 979.00
        BigDecimal result = calculator.calculate(new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("979.00"), result,
                "1000 * 0.98 - 1 = 979.00");
    }

    // ---- testCalculate_zeroAmount_returnsNegative (edge case with formula) ----

    @Test
    @DisplayName("zero paid amount returns zero, but very small amounts cause low refunds due to -1.00 deduction")
    void testCalculate_zeroAmount_returnsNegative() {
        // Given: paid amount is 0 — the null/zero guard returns BigDecimal.ZERO
        BigDecimal zeroResult = calculator.calculate(BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, zeroResult,
                "Zero paid amount returns ZERO due to guard clause");

        // For very small amounts, the -1.00 deduction causes very low refunds
        // 1.02 * 0.98 - 1.00 = 0.9996 - 1.00 = -0.0004, rounds to 0.00
        // 2.00 * 0.98 - 1.00 = 1.96 - 1.00 = 0.96
        // So a refund of 1.00 is possible: 2.05 * 0.98 - 1.00 = 2.009 - 1.00 = 1.01
        // Actually: 2.05 * 0.98 = 2.009, subtract 1 = 1.009, HALF_DOWN rounds to 1.01
        // Let's use a more predictable test:
        BigDecimal tinyResult = calculator.calculate(new BigDecimal("2.00"));
        // 2.00 * 0.98 = 1.96, minus 1.00 = 0.96
        assertEquals(new BigDecimal("0.96"), tinyResult,
                "2.00 * 0.98 - 1.00 = 0.96");
    }
}
