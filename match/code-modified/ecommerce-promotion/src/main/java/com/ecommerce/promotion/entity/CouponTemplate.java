package com.ecommerce.promotion.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Template for a coupon that can be claimed by users.
 * Defines the coupon's discount rules, validity period, and usage limits.
 */
@Entity
@Table(name = "coupon_template")
public class CouponTemplate extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(name = "discount_value", precision = 10, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "threshold_amount", precision = 12, scale = 2)
    private BigDecimal thresholdAmount;

    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "issued_quantity")
    private Integer issuedQuantity;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * JSON array of applicable category IDs.
     * If null or empty, the coupon applies to all categories.
     */
    @Column(name = "applicable_category_ids", columnDefinition = "TEXT")
    private String applicableCategoryIds;

    /**
     * JSON array of specific product (SKU) IDs.
     * If null or empty, the coupon applies to all products.
     */
    @Column(name = "applicable_product_ids", columnDefinition = "TEXT")
    private String applicableProductIds;

    @Column(name = "per_user_limit")
    private Integer perUserLimit;

    /**
     * Template status: ACTIVE or INACTIVE.
     */
    @Column(nullable = false)
    private String status;

    // ---- constructors ----

    public CouponTemplate() {
    }

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

    public Integer getIssuedQuantity() {
        return issuedQuantity;
    }

    public void setIssuedQuantity(Integer issuedQuantity) {
        this.issuedQuantity = issuedQuantity;
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

    public String getApplicableCategoryIds() {
        return applicableCategoryIds;
    }

    public void setApplicableCategoryIds(String applicableCategoryIds) {
        this.applicableCategoryIds = applicableCategoryIds;
    }

    public String getApplicableProductIds() {
        return applicableProductIds;
    }

    public void setApplicableProductIds(String applicableProductIds) {
        this.applicableProductIds = applicableProductIds;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
