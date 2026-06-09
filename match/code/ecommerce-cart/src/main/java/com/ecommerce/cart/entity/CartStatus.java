package com.ecommerce.cart.entity;

/**
 * Cart lifecycle:
 * ACTIVE -> CONVERTED (to order) or EXPIRED (TTL exceeded)
 */
public enum CartStatus {
    ACTIVE,
    CONVERTED,
    EXPIRED
}
