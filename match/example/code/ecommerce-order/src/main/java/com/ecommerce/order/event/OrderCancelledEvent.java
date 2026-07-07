package com.ecommerce.order.event;

import com.ecommerce.common.event.AbstractDomainEvent;

/**
 * Event published when an order is cancelled.
 * Listened to by the inventory module to release reserved stock,
 * and by the payment module to process refunds if applicable.
 */
public class OrderCancelledEvent extends AbstractDomainEvent {

    private final Long orderId;
    private final Long userId;

    public OrderCancelledEvent(Object source, Long orderId, Long userId) {
        super(source);
        this.orderId = orderId;
        this.userId = userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }
}
