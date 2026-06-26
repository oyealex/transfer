package com.ecommerce.blackbox.common;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds per-test-run identity values so every test case uses globally unique
 * business keys. All lifecycle-aware helpers (email, phone, SKU code, coupon
 * code, external order number, client payment number) derive from
 * {@code testRunId} to prevent cross-test data collisions.
 */
public class TestRunContext {

    private final String testRunId;
    private String adminToken;

    public TestRunContext(String testRunId) {
        this.testRunId = testRunId;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public String getTestRunId() {
        return testRunId;
    }

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    // ------------------------------------------------------------------
    // Unique business-key helpers
    // ------------------------------------------------------------------

    public String uniqueEmail() {
        return "user-" + testRunId + "@example.com";
    }

    /**
     * Returns a random phone number matching the pattern 139xxxxxxxx
     * (8 random digits), seeded for uniqueness.
     */
    public String uniquePhone() {
        return "139" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000));
    }

    public String uniqueSkuCode() {
        return "SKU-" + testRunId;
    }

    public String uniqueCouponCode() {
        return "CP-" + testRunId;
    }

    public String uniqueExternalOrderNo() {
        return "EO-" + testRunId;
    }

    public String uniqueClientPaymentNo() {
        return "PAY-" + testRunId;
    }
}
