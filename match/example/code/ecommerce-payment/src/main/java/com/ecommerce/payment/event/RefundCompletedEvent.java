package com.ecommerce.payment.event;

import com.ecommerce.common.event.AbstractDomainEvent;

import java.math.BigDecimal;

public class RefundCompletedEvent extends AbstractDomainEvent {

    private final String refundNo;
    private final String paymentNo;
    private final Long orderId;
    private final Long userId;
    private final BigDecimal refundAmount;

    public RefundCompletedEvent(Object source, String refundNo, String paymentNo,
                                Long orderId, Long userId, BigDecimal refundAmount) {
        super(source);
        this.refundNo = refundNo;
        this.paymentNo = paymentNo;
        this.orderId = orderId;
        this.userId = userId;
        this.refundAmount = refundAmount;
    }

    public String getRefundNo() { return refundNo; }
    public String getPaymentNo() { return paymentNo; }
    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public BigDecimal getRefundAmount() { return refundAmount; }
}
