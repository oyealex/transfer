package com.ecommerce.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OrderTotalCalculator}.
 */
@DisplayName("OrderTotalCalculator")
class OrderTotalCalculatorTest {

    private final OrderTotalCalculator calculator = new OrderTotalCalculator();

    // ======================== payable amount calculation ========================

    @Test
    @DisplayName("calculate payable amount with basic fees")
    void testCalculate_basicFees() {
        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("2.00");
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;

        BigDecimal payableAmount = calculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

        assertThat(payableAmount).isEqualTo(new BigDecimal("102.00"));
    }

    @Test
    @DisplayName("calculate payable amount with free shipping")
    void testCalculate_freeShipping() {
        BigDecimal itemTotal = new BigDecimal("200.00");
        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal packagingFee = new BigDecimal("2.00");
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;

        BigDecimal payableAmount = calculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

        assertThat(payableAmount).isEqualTo(new BigDecimal("202.00"));
    }

    @Test
    @DisplayName("calculate payable amount with discount")
    void testCalculate_withDiscount() {
        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("2.00");
        BigDecimal discountAmount = new BigDecimal("5.00");
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;

        BigDecimal payableAmount = calculator.calculate(
                itemTotal, shippingFee, packagingFee, discountAmount, pointsDeductionAmount);

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

        assertThat(payableAmount).isEqualTo(new BigDecimal("52.00"));
    }

    @Test
    @DisplayName("calculate payable amount with points deduction")
    void testCalculate_withPointsDeduction() {
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
        BigDecimal discountAmount = new BigDecimal("5.00");
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
