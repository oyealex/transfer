package com.ecommerce.loyalty.query;

import java.math.BigDecimal;

/**
 * Write-side interface for loyalty commands.
 * Implemented by {@link com.ecommerce.loyalty.service.LoyaltyPointService}
 * and {@link com.ecommerce.loyalty.service.PointsExpireService}.
 */
public interface LoyaltyCommandService {

    /**
     * Award points on order payment success.
     *
     * <p>Calculation: orderAmount * levelMultiplier * activityMultiplier.
     *
     * @param userId             the user ID
     * @param orderAmount        the order's payable amount
     * @param activityMultiplier promotional activity multiplier (default 1.0)
     * @return the number of points earned
     */
    int earnPaymentPoints(Long userId, BigDecimal orderAmount, double activityMultiplier);

    /**
     * Redeem points toward an order payment.
     *
     * <p>Applies the 10,000-point cap and 50%-of-order-amount cap.
     *
     * @param userId      the user ID
     * @param points      the number of points the user wishes to redeem
     * @param orderAmount the order's payable amount
     * @return the number of points actually redeemed
     */
    int redeemPoints(Long userId, int points, BigDecimal orderAmount);

    /**
     * Process expired points. Points older than the configured expire-months
     * should be moved from available to expired balance.
     */
    void expirePoints();
}
