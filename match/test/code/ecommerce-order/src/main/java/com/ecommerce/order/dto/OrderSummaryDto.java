package com.ecommerce.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Compact order summary DTO for dashboard cards and quick views.
 */
public class OrderSummaryDto {

    private Long orderId;
    private String orderNo;
    private String status;
    private int itemCount;
    private BigDecimal payableAmount;
    private BigDecimal paidAmount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean expired;
    private boolean canCancel;
    private boolean canPay;

    public OrderSummaryDto() {
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public BigDecimal getPayableAmount() { return payableAmount; }
    public void setPayableAmount(BigDecimal payableAmount) { this.payableAmount = payableAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }

    public boolean isCanCancel() { return canCancel; }
    public void setCanCancel(boolean canCancel) { this.canCancel = canCancel; }

    public boolean isCanPay() { return canPay; }
    public void setCanPay(boolean canPay) { this.canPay = canPay; }
}
