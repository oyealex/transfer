package com.ecommerce.loyalty.entity;

/**
 * Represents the membership tier of a loyalty account.
 * Each level has an associated point-earning multiplier.
 */
public enum MemberLevel {

    NORMAL(1.0),
    SILVER(1.1),
    GOLD(1.2),
    PLATINUM(1.5);

    private final double multiplier;

    MemberLevel(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
