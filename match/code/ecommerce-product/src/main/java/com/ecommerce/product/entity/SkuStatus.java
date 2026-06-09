package com.ecommerce.product.entity;

/**
 * SKU status lifecycle:
 * DRAFT -> ON_SHELF -> OFF_SHELF -> DELETED
 * DRAFT can also go directly to DELETED.
 */
public enum SkuStatus {
    DRAFT,
    ON_SHELF,
    OFF_SHELF,
    DELETED
}
