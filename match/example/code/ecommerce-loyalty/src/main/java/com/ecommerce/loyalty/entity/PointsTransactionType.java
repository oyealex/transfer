package com.ecommerce.loyalty.entity;

/**
 * Types of points transactions in the loyalty system.
 */
public enum PointsTransactionType {

    /** Points earned from order payment, reviews, or promotional activities. */
    EARN,

    /** Points consumed to offset order payment. */
    REDEEM,

    /** Points removed due to expiry. */
    EXPIRE,

    /** Manual adjustment by an administrator. */
    ADJUST
}
