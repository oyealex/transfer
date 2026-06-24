package com.ecommerce.loyalty.query;

import com.ecommerce.loyalty.entity.MemberLevel;

import java.math.BigDecimal;

/**
 * Read-side interface for loyalty queries.
 * Implemented by {@link com.ecommerce.loyalty.service.LoyaltyPointService}.
 */
public interface LoyaltyQueryService {

    /**
     * Get the user's available (usable) points balance.
     *
     * @param userId the user ID
     * @return available points
     */
    int getAvailablePoints(Long userId);

    /**
     * Estimate how many points can be redeemed for a given order amount.
     *
     * <p>Applies the 10,000-point cap and 50%-of-order-amount cap.
     *
     * @param orderAmount the order's payable amount
     * @param userId      the user ID
     * @return estimated redeemable points
     */
    int estimateRedeemPoints(BigDecimal orderAmount, Long userId);

    /**
     * Get the user's current membership level.
     *
     * @param userId the user ID
     * @return the member level
     */
    MemberLevel getMemberLevel(Long userId);

    /**
     * Get the point-earning multiplier for the user's current level.
     *
     * @param userId the user ID
     * @return the level multiplier
     */
    double getMemberMultiplier(Long userId);
}
