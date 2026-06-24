package com.ecommerce.promotion.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A spend-based reduction activity, e.g. "spend $300, get $30 off".
 */
@Entity
@Table(name = "full_reduction_activity")
public class FullReductionActivity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "threshold_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal thresholdAmount;

    @Column(name = "reduction_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal reductionAmount;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * JSON array of applicable category IDs.
     */
    @Column(name = "applicable_category_ids", columnDefinition = "TEXT")
    private String applicableCategoryIds;

    /**
     * Product scope: ALL or SPECIFIC.
     */
    @Column(name = "product_scope", nullable = false)
    private String productScope;

    /**
     * Activity status: ACTIVE or INACTIVE.
     */
    @Column(nullable = false)
    private String status;

    // ---- constructors ----

    public FullReductionActivity() {
    }

    // ---- getters and setters ----

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getThresholdAmount() {
        return thresholdAmount;
    }

    public void setThresholdAmount(BigDecimal thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    public BigDecimal getReductionAmount() {
        return reductionAmount;
    }

    public void setReductionAmount(BigDecimal reductionAmount) {
        this.reductionAmount = reductionAmount;
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

    public String getProductScope() {
        return productScope;
    }

    public void setProductScope(String productScope) {
        this.productScope = productScope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
