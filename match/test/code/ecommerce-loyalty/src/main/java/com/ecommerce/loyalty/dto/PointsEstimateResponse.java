package com.ecommerce.loyalty.dto;

import java.math.BigDecimal;

/**
 * Response DTO for POST /api/v1/loyalty/points/estimate-redeem.
 */
public class PointsEstimateResponse {

    private int maxRedeemablePoints;
    private int actualRedeemPoints;
    private BigDecimal redeemAmount;
    private int remainingPoints;

    public PointsEstimateResponse() {
    }

    public int getMaxRedeemablePoints() {
        return maxRedeemablePoints;
    }

    public void setMaxRedeemablePoints(int maxRedeemablePoints) {
        this.maxRedeemablePoints = maxRedeemablePoints;
    }

    public int getActualRedeemPoints() {
        return actualRedeemPoints;
    }

    public void setActualRedeemPoints(int actualRedeemPoints) {
        this.actualRedeemPoints = actualRedeemPoints;
    }

    public BigDecimal getRedeemAmount() {
        return redeemAmount;
    }

    public void setRedeemAmount(BigDecimal redeemAmount) {
        this.redeemAmount = redeemAmount;
    }

    public int getRemainingPoints() {
        return remainingPoints;
    }

    public void setRemainingPoints(int remainingPoints) {
        this.remainingPoints = remainingPoints;
    }
}
