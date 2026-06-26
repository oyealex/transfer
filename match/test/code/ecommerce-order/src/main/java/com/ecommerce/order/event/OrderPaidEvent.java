package com.ecommerce.order.event;

import com.ecommerce.common.event.AbstractDomainEvent;

import java.math.BigDecimal;

/**
 * Event published after an order payment is confirmed.
 * Listened to by the logistics, loyalty, and notification modules.
 */
public class OrderPaidEvent extends AbstractDomainEvent {

    private final Long orderId;
    private final Long userId;
    private final String paymentNo;
    private final BigDecimal paidAmount;

    public OrderPaidEvent(Object source, Long orderId, Long userId,
                          String paymentNo, BigDecimal paidAmount) {
        super(source);
        this.orderId = orderId;
        this.userId = userId;
        this.paymentNo = paymentNo;
        this.paidAmount = paidAmount;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }
}
