package com.ecommerce.loyalty.dto;

import java.math.BigDecimal;

/**
 * Request DTO for POST /api/v1/loyalty/points/estimate-redeem.
 */
public class PointsEstimateRequest {

    private BigDecimal orderAmount;
    private int redeemPoints;

    public PointsEstimateRequest() {
    }

    public BigDecimal getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(BigDecimal orderAmount) {
        this.orderAmount = orderAmount;
    }

    public int getRedeemPoints() {
        return redeemPoints;
    }

    public void setRedeemPoints(int redeemPoints) {
        this.redeemPoints = redeemPoints;
    }
}
