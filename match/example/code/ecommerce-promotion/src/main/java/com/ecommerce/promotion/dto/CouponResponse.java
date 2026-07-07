package com.ecommerce.promotion.dto;

import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO returned to users showing their claimed coupons.
 */
public class CouponResponse {

    private Long userCouponId;
    private String couponCode;
    private String name;
    private CouponType type;
    private BigDecimal discountValue;
    private BigDecimal thresholdAmount;
    private BigDecimal maxDiscount;
    private LocalDateTime endTime;
    private CouponStatus status;

    // ---- constructors ----

    public CouponResponse() {
    }

    // ---- getters and setters ----

    public Long getUserCouponId() {
        return userCouponId;
    }

    public void setUserCouponId(Long userCouponId) {
        this.userCouponId = userCouponId;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CouponType getType() {
        return type;
    }

    public void setType(CouponType type) {
        this.type = type;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public void setThresholdAmount(BigDecimal thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    public BigDecimal getMaxDiscount() {
        return maxDiscount;
    }

    public void setMaxDiscount(BigDecimal maxDiscount) {
        this.maxDiscount = maxDiscount;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public void setStatus(CouponStatus status) {
        this.status = status;
    }
}
