package com.ecommerce.payment.event;

import com.ecommerce.common.event.AbstractDomainEvent;

public class PaymentFailedEvent extends AbstractDomainEvent {

    private final String paymentNo;
    private final Long orderId;

    public PaymentFailedEvent(Object source, String paymentNo, Long orderId) {
        super(source);
        this.paymentNo = paymentNo;
        this.orderId = orderId;
    }

    public String getPaymentNo() { return paymentNo; }
    public Long getOrderId() { return orderId; }
}
