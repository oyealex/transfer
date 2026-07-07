package com.ecommerce.promotion.entity;

/**
 * Represents the type of a coupon template.
 */
public enum CouponType {
    /**
     * Discount coupon, e.g. 20% off (0.8 means 80% of original price).
     */
    DISCOUNT,

    /**
     * Fixed amount off coupon, e.g. $10 off any order.
     */
    AMOUNT_OFF,

    /**
     * Threshold-based amount off, e.g. $30 off when spending $300.
     */
    THRESHOLD_OFF
}
