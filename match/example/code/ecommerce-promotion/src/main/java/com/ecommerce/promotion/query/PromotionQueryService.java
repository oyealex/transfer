package com.ecommerce.promotion.query;

import com.ecommerce.promotion.dto.CouponResponse;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.entity.FullReductionActivity;

import java.math.BigDecimal;
import java.util.List;

/**
 * Query service interface for promotion data that other modules depend on.
 * Other modules (e.g., cart, order) should depend on this interface rather
 * than directly accessing promotion repositories or services.
 *
 * <p>This is exported via the ecommerce-common module pattern so that
 * other services can reference it without depending on the full
 * ecommerce-promotion module.
 */
public interface PromotionQueryService {

    /**
     * Get coupons available for a user given the current order context.
     *
     * @param userId      the user ID
     * @param orderAmount the order subtotal
     * @param skuIds      SKU IDs in the order
     * @return list of applicable coupons
     */
    List<CouponResponse> getAvailableCoupons(Long userId, BigDecimal orderAmount, List<Long> skuIds);

    /**
     * Get all currently active full-reduction activities.
     *
     * @return list of active full-reduction activities
     */
    List<FullReductionActivity> getActiveFullReductions();

    /**
     * Get the member discount rate for a given membership level.
     *
     * @param userLevel the membership level (e.g., GOLD, SILVER)
     * @return discount rate as a BigDecimal (e.g., 0.95 for 5% off)
     */
    BigDecimal getMemberDiscountRate(String userLevel);

    /**
     * Calculate all applicable discounts for an order.
     *
     * @param items     order line items
     * @param userId    the user ID
     * @param couponIds selected coupon IDs
     * @return calculated discount breakdown
     */
    PromotionCalculateResponse calculateDiscounts(List<PromotionCalculateResponse> items,
                                                   Long userId, List<Long> couponIds);
}
