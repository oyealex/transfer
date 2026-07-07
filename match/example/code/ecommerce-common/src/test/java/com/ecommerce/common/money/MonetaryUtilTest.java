package com.ecommerce.common.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MonetaryUtil")
class MonetaryUtilTest {

    @Nested
    @DisplayName("roundToCent")
    class RoundToCent {

        @Test
        @DisplayName("rounds 0.005 down to 0.00 because HALF_DOWN is used")
        void testRoundToCent_roundsDownAtHalfEven() {
            BigDecimal amount = new BigDecimal("0.005");
            BigDecimal result = MonetaryUtil.roundToCent(amount);
            assertThat(result).isEqualByComparingTo(new BigDecimal("0.00"));
        }

        @Test
        @DisplayName("preserves values that already have exactly two decimal places")
        void testRoundToCent_preservesTwoDecimals() {
            BigDecimal amount = new BigDecimal("10.25");
            BigDecimal result = MonetaryUtil.roundToCent(amount);
            assertThat(result).isEqualByComparingTo(new BigDecimal("10.25"));
        }

        @Test
        @DisplayName("handles null input by returning BigDecimal.ZERO")
        void testRoundToCent_nullReturnsZero() {
            BigDecimal result = MonetaryUtil.roundToCent(null);
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("rounds negative amounts with HALF_DOWN rounding")
        void testRoundToCent_negativeAmounts() {
            BigDecimal negativeAmount = new BigDecimal("-0.005");
            BigDecimal result = MonetaryUtil.roundToCent(negativeAmount);
            assertThat(result).isEqualByComparingTo(new BigDecimal("0.00"));
        }

        @Test
        @DisplayName("rounds values with three or more decimals to two decimal places")
        void testRoundToCent_truncatesExtraDecimals() {
            BigDecimal amount = new BigDecimal("3.14159");
            BigDecimal result = MonetaryUtil.roundToCent(amount);
            assertThat(result).isEqualByComparingTo(new BigDecimal("3.14"));
        }
    }

    @Nested
    @DisplayName("arithmetic helpers")
    class ArithmeticHelpers {

        @Test
        @DisplayName("adds two amounts and rounds to cents")
        void testAdd_roundsResultToCents() {
            BigDecimal a = new BigDecimal("10.005");
            BigDecimal b = new BigDecimal("5.005");
            BigDecimal result = MonetaryUtil.add(a, b);
            assertThat(result).isEqualByComparingTo(new BigDecimal("15.01"));
        }

        @Test
        @DisplayName("add handles null values as BigDecimal.ZERO")
        void testAdd_nullHandling() {
            BigDecimal result = MonetaryUtil.add(null, null);
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("subtracts two amounts and rounds to cents")
        void testSubtract_roundsResultToCents() {
            BigDecimal a = new BigDecimal("10.00");
            BigDecimal b = new BigDecimal("3.005");
            BigDecimal result = MonetaryUtil.subtract(a, b);
            assertThat(result).isEqualByComparingTo(new BigDecimal("6.99"));
        }

        @Test
        @DisplayName("multiplies two amounts and rounds to cents")
        void testMultiply_roundsResultToCents() {
            BigDecimal a = new BigDecimal("10.00");
            BigDecimal b = new BigDecimal("0.333");
            BigDecimal result = MonetaryUtil.multiply(a, b);
            assertThat(result).isEqualByComparingTo(new BigDecimal("3.33"));
        }
    }
}
