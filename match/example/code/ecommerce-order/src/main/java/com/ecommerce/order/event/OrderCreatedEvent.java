package com.ecommerce.order.event;

import com.ecommerce.common.event.AbstractDomainEvent;

import java.math.BigDecimal;

/**
 * Event published when a new order is created.
 * Other modules (payment, notification, etc.) listen for this event.
 */
public class OrderCreatedEvent extends AbstractDomainEvent {

    private final Long orderId;
    private final Long userId;
    private final BigDecimal payableAmount;

    public OrderCreatedEvent(Object source, Long orderId, Long userId, BigDecimal payableAmount) {
        super(source);
        this.orderId = orderId;
        this.userId = userId;
        this.payableAmount = payableAmount;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getPayableAmount() {
        return payableAmount;
    }
}
