package com.ecommerce.loyalty.event;

import com.ecommerce.common.event.AbstractDomainEvent;

import java.math.BigDecimal;

/**
 * Domain event published when an order payment is confirmed.
 * The loyalty module listens for this event to award points.
 *
 * <p>This event is published by the order module after payment confirmation.
 */
public class OrderPaidEvent extends AbstractDomainEvent {

    private final Long orderId;
    private final Long userId;
    private final BigDecimal payableAmount;

    public OrderPaidEvent(Object source, Long orderId, Long userId, BigDecimal payableAmount) {
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
