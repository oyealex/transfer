package com.ecommerce.promotion.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for claiming a coupon.
 */
public class CouponClaimRequest {

    @NotNull
    private Long couponTemplateId;

    // ---- getters and setters ----

    public Long getCouponTemplateId() {
        return couponTemplateId;
    }

    public void setCouponTemplateId(Long couponTemplateId) {
        this.couponTemplateId = couponTemplateId;
    }
}
