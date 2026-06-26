package com.ecommerce.payment.dto;

import com.ecommerce.payment.entity.PaymentMethod;

import java.math.BigDecimal;

public class PayRequest {

    private Long orderId;
    private BigDecimal amount;
    private PaymentMethod method;
    private String clientPaymentNo;

    public PayRequest() {
    }

    public PayRequest(Long orderId, BigDecimal amount, PaymentMethod method, String clientPaymentNo) {
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.clientPaymentNo = clientPaymentNo;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public String getClientPaymentNo() { return clientPaymentNo; }
    public void setClientPaymentNo(String clientPaymentNo) { this.clientPaymentNo = clientPaymentNo; }
}
