package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for sending expiry reminders for unpaid orders.
 */
public class OrderExpiryReminderRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    private boolean forceSend; // Send even if already reminded

    public OrderExpiryReminderRequest() {
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public boolean isForceSend() { return forceSend; }
    public void setForceSend(boolean forceSend) { this.forceSend = forceSend; }
}
