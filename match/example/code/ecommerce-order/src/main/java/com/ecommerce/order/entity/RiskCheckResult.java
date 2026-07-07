package com.ecommerce.order.entity;

/**
 * Result of an order risk check.
 * Used to determine whether an order should be allowed or rejected
 * based on risk analysis.
 */
public class RiskCheckResult {

    /** Whether the risk check passed */
    private boolean passed;

    /** Risk level classification */
    private RiskLevel riskLevel;

    /** Human-readable reason when risk check fails or is elevated */
    private String reason;

    public RiskCheckResult() {
    }

    public RiskCheckResult(boolean passed, RiskLevel riskLevel, String reason) {
        this.passed = passed;
        this.riskLevel = riskLevel;
        this.reason = reason;
    }

    /**
     * Factory method for a passed check.
     */
    public static RiskCheckResult passed() {
        return new RiskCheckResult(true, RiskLevel.LOW, "Risk check passed");
    }

    /**
     * Factory method for a failed check.
     */
    public static RiskCheckResult rejected(RiskLevel riskLevel, String reason) {
        return new RiskCheckResult(false, riskLevel, reason);
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Risk level classification for order risk analysis.
     */
    public enum RiskLevel {
        /** Low risk, order allowed */
        LOW,
        /** Medium risk, order allowed but may be monitored */
        MEDIUM,
        /** High risk, order requires manual review */
        HIGH,
        /** Order should be rejected outright */
        REJECTED
    }
}
