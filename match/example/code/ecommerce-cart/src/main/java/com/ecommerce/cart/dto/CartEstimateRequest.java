package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Request DTO for cart price estimation.
 */
public class CartEstimateRequest {

    private List<Long> couponIds;

    @Min(value = 0, message = "redeemPoints must not be negative")
    private Integer redeemPoints;

    public CartEstimateRequest() {
    }

    public List<Long> getCouponIds() {
        return couponIds;
    }

    public void setCouponIds(List<Long> couponIds) {
        this.couponIds = couponIds;
    }

    public Integer getRedeemPoints() {
        return redeemPoints;
    }

    public void setRedeemPoints(Integer redeemPoints) {
        this.redeemPoints = redeemPoints;
    }
}
