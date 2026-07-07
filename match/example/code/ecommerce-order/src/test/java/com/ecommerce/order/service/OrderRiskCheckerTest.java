package com.ecommerce.order.service;

import com.ecommerce.order.entity.RiskCheckResult;
import com.ecommerce.order.entity.RiskCheckResult.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OrderRiskChecker}.
 */
@DisplayName("OrderRiskChecker")
class OrderRiskCheckerTest {

    private final OrderRiskChecker riskChecker = new OrderRiskChecker();

    // ======================== HIGH risk ========================

    @Test
    @DisplayName("check with amount >= 10000 returns REJECTED (HIGH risk)")
    void testCheck_highAmount_rejected() {
        BigDecimal highAmount = new BigDecimal("10000.00");

        RiskCheckResult result = riskChecker.check(1L, highAmount, Collections.singletonList(10L));

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.getReason()).contains("10000");
        assertThat(result.getReason()).contains("high risk threshold");
    }

    @Test
    @DisplayName("check with amount > 10000 returns REJECTED")
    void testCheck_veryHighAmount_rejected() {
        BigDecimal veryHighAmount = new BigDecimal("50000.00");

        RiskCheckResult result = riskChecker.check(1L, veryHighAmount, Collections.emptyList());

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    // ======================== MEDIUM risk ========================

    @Test
    @DisplayName("check with amount >= 5000 but < 10000 returns MEDIUM risk (passed)")
    void testCheck_mediumAmount_passed() {
        BigDecimal mediumAmount = new BigDecimal("5000.00");

        RiskCheckResult result = riskChecker.check(1L, mediumAmount, Collections.singletonList(10L));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.getReason()).contains("medium risk threshold");
    }

    @Test
    @DisplayName("check with many items (>= 20) returns MEDIUM risk")
    void testCheck_manyItems_mediumRisk() {
        List<Long> manySkus = new java.util.ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            manySkus.add(i);
        }
        // Amount is below MEDIUM threshold
        BigDecimal lowAmount = new BigDecimal("100.00");

        RiskCheckResult result = riskChecker.check(1L, lowAmount, manySkus);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.getReason()).contains("20");
        assertThat(result.getReason()).contains("reseller");
    }

    // ======================== LOW risk ========================

    @Test
    @DisplayName("check with low amount and few items returns LOW risk (passed)")
    void testCheck_lowAmount_passed() {
        BigDecimal lowAmount = new BigDecimal("100.00");

        RiskCheckResult result = riskChecker.check(1L, lowAmount, Collections.singletonList(10L));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("check with null amount is not rejected (amount rule skipped)")
    void testCheck_nullAmount_passes() {
        RiskCheckResult result = riskChecker.check(1L, null, Collections.singletonList(10L));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("check with null skuIds passes")
    void testCheck_nullSkuIds_passes() {
        BigDecimal lowAmount = new BigDecimal("100.00");

        RiskCheckResult result = riskChecker.check(1L, lowAmount, null);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
    }
}
