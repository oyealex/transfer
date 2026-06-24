package com.ecommerce.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request DTO for searching orders with multiple optional filters.
 * Supports both user-facing and admin-facing search queries.
 */
public class OrderSearchRequest {

    private Long userId;
    private String status;
    private String orderNo;
    private String externalOrderNo;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String paymentNo;

    @Min(value = 0, message = "Page must be >= 0")
    private int page = 0;

    @Min(value = 1, message = "Size must be >= 1")
    @Max(value = 100, message = "Size must be <= 100")
    private int size = 20;

    public OrderSearchRequest() {
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getExternalOrderNo() { return externalOrderNo; }
    public void setExternalOrderNo(String externalOrderNo) { this.externalOrderNo = externalOrderNo; }

    public LocalDateTime getCreatedAfter() { return createdAfter; }
    public void setCreatedAfter(LocalDateTime createdAfter) { this.createdAfter = createdAfter; }

    public LocalDateTime getCreatedBefore() { return createdBefore; }
    public void setCreatedBefore(LocalDateTime createdBefore) { this.createdBefore = createdBefore; }

    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }

    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }

    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
