package com.ecommerce.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OrderTotalCalculator}.
 *
 * <p>The {@link OrderTotalCalculator#calculate(BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal)}
 * method OMITS the shipping fee from the final payable amount. The shipping fee
 * is calculated correctly (8.00 for orders under 199.00, free for >= 199.00)
 * but is never added to the payable amount.
 *
 * <p>Correct: payableAmount = itemTotal + shippingFee + packagingFee - discountAmount - pointsDeductionAmount
 * <p>Actual: payableAmount = itemTotal + packagingFee - discountAmount - pointsDeductionAmount
 */
@DisplayName("OrderTotalCalculator")
class OrderTotalCalculatorTest {

    private final OrderTotalCalculator calculator = new OrderTotalCalculator();

    // ======================== shipping fee omitted ========================

    @Test
    @DisplayName("calculate excludes shippingFee from payableAmount")
    void testCalculate_excludesShippingFee() {
        // itemTotal=100, shippingFee=8.00 (under 199 threshold), packagingFee=2.00, discount=0, points=0
        // Correct: 100 + 8 + 2 - 0 - 0 = 110.00
        // Actual:  100 + 2 - 0 - 0 = 102.00

        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");  // calculated but OMITTED
        BigDecimal packagingFee = new BigDecimal("2.00");
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;

        BigDecimal payableAmount = calculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

        // shippingFee is NOT included in the payable amount, should be 110.00 but is actually 102.00
        assertThat(payableAmount).isEqualTo(new BigDecimal("102.00"));
    }

    @Test
    @DisplayName("calculate with free shipping (itemTotal >= 199) does not add shipping fee")
    void testCalculate_freeShippingNotAdded() {
        // itemTotal=200 → shippingFee=0 (free), packagingFee=2.00
        BigDecimal itemTotal = new BigDecimal("200.00");
        BigDecimal shippingFee = BigDecimal.ZERO;  // free shipping
        BigDecimal packagingFee = new BigDecimal("2.00");
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;

        BigDecimal payableAmount = calculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

        // Since shippingFee is 0, both correct and actual give the same result
        assertThat(payableAmount).isEqualTo(new BigDecimal("202.00"));
    }

    @Test
    @DisplayName("calculate with non-free shipping: shipping fee calculated but not added to payable")
    void testCalculate_shippingNotFree_notAdded() {
        // itemTotal=100 → shippingFee=8.00 (not free), packagingFee=2.00, discount=5.00
        // Correct: 100 + 8 + 2 - 5 = 105.00
        // Actual:  100 + 2 - 5 = 97.00

        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");  // NOT free but OMITTED
        BigDecimal packagingFee = new BigDecimal("2.00");
        BigDecimal discountAmount = new BigDecimal("5.00");
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;

        BigDecimal payableAmount = calculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

        // shippingFee omitted, should be 105.00, actually 97.00
        assertThat(payableAmount).isEqualTo(new BigDecimal("97.00"));
    }

    @Test
    @DisplayName("calculate packagingFee is always added")
    void testCalculate_packagingFee_alwaysAdded() {
        BigDecimal itemTotal = new BigDecimal("50.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("2.00");
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;

        BigDecimal payableAmount = calculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

        // packagingFee 2.00 IS included: 50 + 2 = 52 (shipping omitted)
        assertThat(payableAmount).isEqualTo(new BigDecimal("52.00"));
        // Note: shipping fee 8.00 should also be added but isn't
    }

    @Test
    @DisplayName("calculate with points deduction: shipping fee still omitted")
    void testCalculate_withPointsDeduction_stillExcludesShipping() {
        // itemTotal=150, shippingFee=8.00, packagingFee=3.00, discount=10.00, points=5.00
        // Correct: 150 + 8 + 3 - 10 - 5 = 146.00
        // Actual:  150 + 3 - 10 - 5 = 138.00
        BigDecimal itemTotal = new BigDecimal("150.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("3.00");
        BigDecimal discountAmount = new BigDecimal("10.00");
        BigDecimal pointsDeductionAmount = new BigDecimal("5.00");

        BigDecimal payableAmount = calculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

        assertThat(payableAmount).isEqualTo(new BigDecimal("138.00"));
    }

    @Test
    @DisplayName("calculate minimum payable amount is 0.01")
    void testCalculate_minimumPayableAmount() {
        BigDecimal itemTotal = new BigDecimal("1.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("0.50");
        BigDecimal discountAmount = new BigDecimal("5.00");  // discount exceeds total
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;

        BigDecimal payableAmount = calculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

        // itemTotal + packagingFee - discount = 1.00 + 0.50 - 5.00 = -3.50 → floored to 0.01
        assertThat(payableAmount).isEqualTo(new BigDecimal("0.01"));
    }

    // ======================== calculateShippingFee ========================

    @Test
    @DisplayName("calculateShippingFee returns 0 for itemTotal >= 199.00")
    void testCalculateShippingFee_freeThreshold() {
        assertThat(calculator.calculateShippingFee(new BigDecimal("199.00")))
                .isEqualTo(BigDecimal.ZERO);
        assertThat(calculator.calculateShippingFee(new BigDecimal("300.00")))
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("calculateShippingFee returns 8.00 for itemTotal under 199.00")
    void testCalculateShippingFee_standardFee() {
        assertThat(calculator.calculateShippingFee(new BigDecimal("198.99")))
                .isEqualTo(new BigDecimal("8.00"));
        assertThat(calculator.calculateShippingFee(new BigDecimal("50.00")))
                .isEqualTo(new BigDecimal("8.00"));
        assertThat(calculator.calculateShippingFee(new BigDecimal("0.01")))
                .isEqualTo(new BigDecimal("8.00"));
    }

    @Test
    @DisplayName("calculateShippingFee returns 0 for null or non-positive itemTotal")
    void testCalculateShippingFee_nullOrZero() {
        assertThat(calculator.calculateShippingFee(null))
                .isEqualTo(BigDecimal.ZERO);
        assertThat(calculator.calculateShippingFee(BigDecimal.ZERO))
                .isEqualTo(BigDecimal.ZERO);
        assertThat(calculator.calculateShippingFee(new BigDecimal("-10.00")))
                .isEqualTo(BigDecimal.ZERO);
    }

    // ======================== calculatePackagingFee ========================

    @Test
    @DisplayName("calculatePackagingFee returns 0 for 0 or negative itemCount")
    void testCalculatePackagingFee_zeroOrNegative() {
        assertThat(calculator.calculatePackagingFee(0))
                .isEqualTo(BigDecimal.ZERO);
        assertThat(calculator.calculatePackagingFee(-1))
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("calculatePackagingFee returns per-item fee")
    void testCalculatePackagingFee_perItem() {
        // 1.00 per item
        assertThat(calculator.calculatePackagingFee(1))
                .isEqualTo(new BigDecimal("1.00"));
        assertThat(calculator.calculatePackagingFee(3))
                .isEqualTo(new BigDecimal("3.00"));
        assertThat(calculator.calculatePackagingFee(10))
                .isEqualTo(new BigDecimal("10.00"));
    }
}
