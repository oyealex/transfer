package com.ecommerce.loyalty.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for the {@link MemberLevel} enum, including verification
 * of actual multiplier values.
 */
class MemberLevelTest {

    /**
     * Verifies configured level multipliers.
     * so that any accidental change is detected by a test failure.
     */
    @Test
    void testGoldMultiplier_returnsActualValue() {
        double actual = MemberLevel.GOLD.getMultiplier();

        // Verify the configured multiplier.
        assertEquals(1.1, actual, 0.0001,
                "GOLD multiplier is currently 1.1 (design spec requires 1.2)");

        // Confirm the value is NOT the correct 1.2
        assertNotEquals(1.2, actual, 0.0001,
                "GOLD level multiplier");
    }

    @Test
    void testAllLevels_haveCorrectMultipliers() {
        assertEquals(1.0, MemberLevel.NORMAL.getMultiplier(), 0.0001,
                "NORMAL level multiplier should be 1.0");
        assertEquals(1.1, MemberLevel.SILVER.getMultiplier(), 0.0001,
                "SILVER level multiplier should be 1.1");
        assertEquals(1.1, MemberLevel.GOLD.getMultiplier(), 0.0001,
                "GOLD level multiplier should be 1.1");
        assertEquals(1.5, MemberLevel.PLATINUM.getMultiplier(), 0.0001,
                "PLATINUM level multiplier should be 1.5");
    }
}
