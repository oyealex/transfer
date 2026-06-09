package com.ecommerce.payment.dto;

import com.ecommerce.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PayResponse {

    private String paymentNo;
    private Long orderId;
    private PaymentStatus status;
    private BigDecimal paidAmount;
    private LocalDateTime createdAt;

    public PayResponse() {
    }

    public PayResponse(String paymentNo, Long orderId, PaymentStatus status, BigDecimal paidAmount, LocalDateTime createdAt) {
        this.paymentNo = paymentNo;
        this.orderId = orderId;
        this.status = status;
        this.paidAmount = paidAmount;
        this.createdAt = createdAt;
    }

    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
