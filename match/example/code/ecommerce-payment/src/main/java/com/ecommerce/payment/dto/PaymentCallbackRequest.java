package com.ecommerce.payment.dto;

import java.math.BigDecimal;

public class PaymentCallbackRequest {

    private String paymentNo;
    private Long orderId;
    private String status;
    private BigDecimal amount;
    private String callbackSequence;
    private String signature;

    public PaymentCallbackRequest() {
    }

    public PaymentCallbackRequest(String paymentNo, Long orderId, String status,
                                  BigDecimal amount, String callbackSequence, String signature) {
        this.paymentNo = paymentNo;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.callbackSequence = callbackSequence;
        this.signature = signature;
    }

    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCallbackSequence() { return callbackSequence; }
    public void setCallbackSequence(String callbackSequence) { this.callbackSequence = callbackSequence; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
