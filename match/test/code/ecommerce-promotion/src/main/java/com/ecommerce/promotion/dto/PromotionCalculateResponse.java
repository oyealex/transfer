package com.ecommerce.promotion.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO returned with promotion calculation results.
 */
public class PromotionCalculateResponse {

    private BigDecimal itemTotal;
    private BigDecimal fullReductionDiscount;
    private BigDecimal couponDiscount;
    private BigDecimal memberDiscount;
    private BigDecimal totalDiscount;
    private BigDecimal finalAmount;
    private List<ApplicableCoupon> applicableCoupons;

    /**
     * A coupon that applies to this calculation.
     */
    public static class ApplicableCoupon {

        private Long couponId;
        private String couponCode;
        private String name;
        private BigDecimal discountAmount;

        public Long getCouponId() {
            return couponId;
        }

        public void setCouponId(Long couponId) {
            this.couponId = couponId;
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

        public BigDecimal getDiscountAmount() {
            return discountAmount;
        }

        public void setDiscountAmount(BigDecimal discountAmount) {
            this.discountAmount = discountAmount;
        }
    }

    // ---- getters and setters ----

    public BigDecimal getItemTotal() {
        return itemTotal;
    }

    public void setItemTotal(BigDecimal itemTotal) {
        this.itemTotal = itemTotal;
    }

    public BigDecimal getFullReductionDiscount() {
        return fullReductionDiscount;
    }

    public void setFullReductionDiscount(BigDecimal fullReductionDiscount) {
        this.fullReductionDiscount = fullReductionDiscount;
    }

    public BigDecimal getCouponDiscount() {
        return couponDiscount;
    }

    public void setCouponDiscount(BigDecimal couponDiscount) {
        this.couponDiscount = couponDiscount;
    }

    public BigDecimal getMemberDiscount() {
        return memberDiscount;
    }

    public void setMemberDiscount(BigDecimal memberDiscount) {
        this.memberDiscount = memberDiscount;
    }

    public BigDecimal getTotalDiscount() {
        return totalDiscount;
    }

    public void setTotalDiscount(BigDecimal totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(BigDecimal finalAmount) {
        this.finalAmount = finalAmount;
    }

    public List<ApplicableCoupon> getApplicableCoupons() {
        return applicableCoupons;
    }

    public void setApplicableCoupons(List<ApplicableCoupon> applicableCoupons) {
        this.applicableCoupons = applicableCoupons;
    }
}
