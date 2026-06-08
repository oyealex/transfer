package com.ecommerce.promotion.dto;

import com.ecommerce.promotion.entity.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for creating a coupon template (admin).
 */
public class CouponCreateRequest {

    @NotBlank
    private String name;

    @NotNull
    private CouponType type;

    private BigDecimal discountValue;

    private BigDecimal thresholdAmount;

    private BigDecimal maxDiscount;

    private Integer totalQuantity;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private List<Long> applicableCategoryIds;

    private List<Long> applicableProductIds;

    private Integer perUserLimit;

    // ---- getters and setters ----

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

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public List<Long> getApplicableCategoryIds() {
        return applicableCategoryIds;
    }

    public void setApplicableCategoryIds(List<Long> applicableCategoryIds) {
        this.applicableCategoryIds = applicableCategoryIds;
    }

    public List<Long> getApplicableProductIds() {
        return applicableProductIds;
    }

    public void setApplicableProductIds(List<Long> applicableProductIds) {
        this.applicableProductIds = applicableProductIds;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }
}
