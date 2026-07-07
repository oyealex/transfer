package com.ecommerce.order.integration;

import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.loyalty.query.LoyaltyQueryService;
import com.ecommerce.order.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Centralizes all loyalty/points integration within the order module.
 *
 * <p>This service handles the interaction between orders and the loyalty module,
 * including:
 * <ul>
 *   <li>Estimating redeemable points for an order</li>
 *   <li>Calculating points deduction amounts</li>
 *   <li>Getting member discounts and multipliers</li>
 *   <li>Validating points redemption against business rules</li>
 * </ul>
 *
 * <p>Points rules applied:
 * <ul>
 *   <li>100 points = 1 yuan (1 point = 0.01 yuan)</li>
 *   <li>Maximum 10,000 points per order (100 yuan)</li>
 *   <li>Points deduction cannot exceed 50% of order amount</li>
 *   <li>Points are deducted from available balance at order time (not payment time)</li>
 * </ul>
 */
@Service
public class LoyaltyIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyIntegrationService.class);

    private static final BigDecimal POINTS_TO_YUAN_RATE = new BigDecimal("0.01"); // 1 point = 0.01 yuan
    private static final int MAX_REDEEM_POINTS = 10_000;
    private static final BigDecimal MAX_REDEEM_RATIO = new BigDecimal("0.5");

    private final LoyaltyQueryService loyaltyQueryService;

    public LoyaltyIntegrationService(LoyaltyQueryService loyaltyQueryService) {
        this.loyaltyQueryService = loyaltyQueryService;
    }

    /**
     * Calculate the amount that would be deducted if the user redeems the given number of points.
     * Applies the 10,000-point cap and 50% of order amount cap.
     *
     * @param userId        the user ID
     * @param orderAmount   the preliminary payable amount (before points deduction)
     * @param requestedPoints the number of points the user wants to redeem
     * @return a PointsCalculationResult with the actual redeemable points and deduction amount
     */
    public PointsCalculationResult calculatePointsDeduction(Long userId, BigDecimal orderAmount,
                                                             int requestedPoints) {
        if (userId == null || orderAmount == null
                || orderAmount.compareTo(BigDecimal.ZERO) <= 0
                || requestedPoints <= 0) {
            return PointsCalculationResult.zero();
        }

        try {
            // Get the maximum redeemable points from the loyalty module
            int maxRedeemable = loyaltyQueryService.estimateRedeemPoints(orderAmount, userId);

            // Cap at user's requested amount
            int actualPoints = Math.min(requestedPoints, maxRedeemable);

            if (actualPoints <= 0) {
                return PointsCalculationResult.zero();
            }

            // Calculate deduction amount: points * 0.01
            BigDecimal deductionAmount = MonetaryUtil.multiply(
                    BigDecimal.valueOf(actualPoints), POINTS_TO_YUAN_RATE);

            // Ensure deduction doesn't exceed 50% of order amount
            BigDecimal maxDeduction = MonetaryUtil.multiply(orderAmount, MAX_REDEEM_RATIO);
            if (deductionAmount.compareTo(maxDeduction) > 0) {
                deductionAmount = maxDeduction;
                // Recalculate points
                actualPoints = deductionAmount.divide(POINTS_TO_YUAN_RATE, 0, BigDecimal.ROUND_DOWN)
                        .intValue();
            }

            log.info("Points deduction: userId={}, requested={}, actual={}, amount={}",
                    userId, requestedPoints, actualPoints, deductionAmount);

            return new PointsCalculationResult(actualPoints, deductionAmount);

        } catch (Exception e) {
            log.warn("Failed to calculate points deduction, returning zero: {}", e.getMessage());
            return PointsCalculationResult.zero();
        }
    }

    /**
     * Get the member discount rate for the user's current level.
     */
    public BigDecimal getMemberDiscountRate(Long userId) {
        try {
            double multiplier = loyaltyQueryService.getMemberMultiplier(userId);
            // Lower multiplier = lower discount rate
            // NORMAL: 1.0 -> 0% discount
            // SILVER: 1.1 -> ~9% discount equivalent
            // GOLD: 1.1
            // PLATINUM: 1.5 -> ~33% discount equivalent
            // For discount purposes, use inverse relationship
            BigDecimal multiplierBd = BigDecimal.valueOf(multiplier);
            // Discount rate = 1 - 1/multiplier (capped at 0)
            BigDecimal discountRate = BigDecimal.ONE.subtract(
                    BigDecimal.ONE.divide(multiplierBd, 4, BigDecimal.ROUND_HALF_UP));
            return discountRate.max(BigDecimal.ZERO);
        } catch (Exception e) {
            log.warn("Failed to get member discount rate: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get the member level name for display.
     */
    public String getMemberLevelName(Long userId) {
        try {
            return loyaltyQueryService.getMemberLevel(userId).name();
        } catch (Exception e) {
            log.warn("Failed to get member level: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * Get the available points balance for a user.
     */
    public int getAvailablePoints(Long userId) {
        try {
            return loyaltyQueryService.getAvailablePoints(userId);
        } catch (Exception e) {
            log.warn("Failed to get available points: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Validate that the requested points redemption is within bounds.
     * Returns validation errors, or empty string if valid.
     */
    public String validatePointsRedemption(Long userId, int requestedPoints, BigDecimal orderAmount) {
        if (requestedPoints <= 0) {
            return null; // No points requested, valid
        }

        if (requestedPoints > MAX_REDEEM_POINTS) {
            return "Cannot redeem more than " + MAX_REDEEM_POINTS
                    + " points per order (requested: " + requestedPoints + ")";
        }

        BigDecimal maxDeduction = MonetaryUtil.multiply(orderAmount, MAX_REDEEM_RATIO);
        BigDecimal requestedDeduction = MonetaryUtil.multiply(
                BigDecimal.valueOf(requestedPoints), POINTS_TO_YUAN_RATE);

        if (requestedDeduction.compareTo(maxDeduction) > 0) {
            return "Points deduction ($" + requestedDeduction
                    + ") cannot exceed 50% of order amount ($" + maxDeduction + ")";
        }

        int available = getAvailablePoints(userId);
        if (requestedPoints > available) {
            return "Insufficient points: requested " + requestedPoints
                    + " but only " + available + " available";
        }

        return null; // Valid
    }

    /**
     * Calculate the amount a user earned from an order (for display purposes).
     * Actual earning happens after payment.
     */
    public int estimateEarnedPoints(Long userId, BigDecimal orderAmount) {
        try {
            return loyaltyQueryService.estimateRedeemPoints(orderAmount, userId);
        } catch (Exception e) {
            log.warn("Failed to estimate earned points: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Result of points deduction calculation.
     */
    public static class PointsCalculationResult {
        private final int points;
        private final BigDecimal amount;

        public PointsCalculationResult(int points, BigDecimal amount) {
            this.points = points;
            this.amount = amount;
        }

        public static PointsCalculationResult zero() {
            return new PointsCalculationResult(0, BigDecimal.ZERO);
        }

        public int getPoints() { return points; }
        public BigDecimal getAmount() { return amount; }
    }
}
