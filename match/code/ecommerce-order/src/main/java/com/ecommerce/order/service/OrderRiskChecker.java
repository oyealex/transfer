package com.ecommerce.order.service;

import com.ecommerce.order.entity.RiskCheckResult;
import com.ecommerce.order.entity.RiskCheckResult.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Performs risk analysis on incoming orders before they are created.
 */
@Component
public class OrderRiskChecker {

    private static final Logger log = LoggerFactory.getLogger(OrderRiskChecker.class);

    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = new BigDecimal("5000.00");
    private static final int SUSPICIOUS_ITEM_COUNT = 20;

    /**
     * Perform risk analysis on an order.
     *
     * @param userId      the user placing the order
     * @param orderAmount the total order amount
     * @param skuIds      the SKU IDs in the order
     * @return the risk check result
     */
    public RiskCheckResult check(Long userId, BigDecimal orderAmount, List<Long> skuIds) {
        log.info("Performing risk check for userId={}, amount={}, itemCount={}",
                userId, orderAmount, skuIds != null ? skuIds.size() : 0);

        // Rule 1: Amount exceeds high risk threshold => HIGH risk
        if (orderAmount != null && orderAmount.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            log.warn("HIGH risk: order amount {} exceeds threshold {}", orderAmount, HIGH_RISK_THRESHOLD);
            return RiskCheckResult.rejected(RiskLevel.HIGH,
                    "Order amount " + orderAmount + " exceeds high risk threshold "
                            + HIGH_RISK_THRESHOLD);
        }

        // Rule 2: Amount exceeds medium risk threshold => MEDIUM risk (still allowed)
        if (orderAmount != null && orderAmount.compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
            log.warn("MEDIUM risk: order amount {} exceeds threshold {}", orderAmount, MEDIUM_RISK_THRESHOLD);
            return new RiskCheckResult(true, RiskLevel.MEDIUM,
                    "Order amount " + orderAmount + " exceeds medium risk threshold");
        }

        // Rule 3: Suspiciously large number of unique items => MEDIUM risk
        if (skuIds != null && skuIds.size() >= SUSPICIOUS_ITEM_COUNT) {
            log.warn("MEDIUM risk: item count {} exceeds suspicious threshold {}",
                    skuIds.size(), SUSPICIOUS_ITEM_COUNT);
            return new RiskCheckResult(true, RiskLevel.MEDIUM,
                    "Order contains " + skuIds.size() + " unique items, potential reseller activity");
        }

        log.debug("Risk check passed for userId={}: LOW risk", userId);
        return RiskCheckResult.passed();
    }
}
