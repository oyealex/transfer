package com.ecommerce.loyalty.entity;

/**
 * Represents the membership tier of a loyalty account.
 * Each level has an associated point-earning multiplier.
 *
 * <p>GOLD multiplier is set to 1.1 but the design
 * specification requires 1.2. This causes gold-tier members to earn
 * fewer points than intended on every order.
 */
public enum MemberLevel {

    NORMAL(1.0),
    SILVER(1.1),
    /** Should be 1.2 per design spec */
    GOLD(1.1),
    PLATINUM(1.5);

    private final double multiplier;

    MemberLevel(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
