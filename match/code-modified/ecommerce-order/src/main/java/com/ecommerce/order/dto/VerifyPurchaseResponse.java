package com.ecommerce.order.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for purchase verification.
 */
public class VerifyPurchaseResponse {

    private boolean purchased;
    private Long orderId;
    private LocalDateTime deliveredAt;

    public VerifyPurchaseResponse() {
    }

    public VerifyPurchaseResponse(boolean purchased, Long orderId, LocalDateTime deliveredAt) {
        this.purchased = purchased;
        this.orderId = orderId;
        this.deliveredAt = deliveredAt;
    }

    public boolean isPurchased() {
        return purchased;
    }

    public void setPurchased(boolean purchased) {
        this.purchased = purchased;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
