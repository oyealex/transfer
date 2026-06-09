package com.ecommerce.promotion.entity;

/**
 * Represents the status of a user's coupon.
 */
public enum CouponStatus {
    /**
     * Coupon is available for use.
     */
    AVAILABLE,

    /**
     * Coupon has been used in an order.
     */
    USED,

    /**
     * Coupon has passed its expiry date.
     */
    EXPIRED
}
