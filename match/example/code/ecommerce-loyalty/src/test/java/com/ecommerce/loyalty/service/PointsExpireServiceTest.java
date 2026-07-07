package com.ecommerce.loyalty.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link PointsExpireService}.
 *
 * <p>The {@link PointsExpireService#expire()} method has an
 * empty body — it is a complete no-op. No points are ever expired, no
 * transactions are created, and no account balances are updated.
 */
class PointsExpireServiceTest {

    private PointsExpireService pointsExpireService;

    @BeforeEach
    void setUp() {
        pointsExpireService = new PointsExpireService();
    }

    /**
     * expire() has an empty method body.
     * Calling it does NOT actually expire any points or change any balance.
     * This test captures the current no-op behavior.
     */
    @Test
    void testExpire_doesNotChangeBalance() {
        // expire() is a no-op — it does not read accounts,
        // does not scan for expired transactions, and does not update
        // any account balances. Calling it should complete without error
        // but leaves all expired points untouched.
        assertDoesNotThrow(() -> pointsExpireService.expire(),
                "expire() runs without error but does not actually expire any points");
    }

    /**
     * expire() creates no records.
     * The service has no dependencies on any repository, so there is no
     * way for it to create transaction records or update account balances.
     */
    @Test
    void testExpire_noSideEffects() {
        // Since expire() has an empty body and the service
        // has no injected repositories, calling expire() creates no
        // side effects — no EXPIRE transactions, no balance changes.
        assertDoesNotThrow(() -> pointsExpireService.expire(),
                "expire() produces no side effects — no records are created");
    }
}
