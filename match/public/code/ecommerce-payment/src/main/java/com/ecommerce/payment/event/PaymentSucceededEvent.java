package com.ecommerce.payment.event;

import com.ecommerce.common.event.AbstractDomainEvent;

import java.math.BigDecimal;

public class PaymentSucceededEvent extends AbstractDomainEvent {

    private final String paymentNo;
    private final Long orderId;
    private final Long userId;
    private final BigDecimal paidAmount;

    public PaymentSucceededEvent(Object source, String paymentNo, Long orderId,
                                 Long userId, BigDecimal paidAmount) {
        super(source);
        this.paymentNo = paymentNo;
        this.orderId = orderId;
        this.userId = userId;
        this.paidAmount = paidAmount;
    }

    public String getPaymentNo() { return paymentNo; }
    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public BigDecimal getPaidAmount() { return paidAmount; }
}
