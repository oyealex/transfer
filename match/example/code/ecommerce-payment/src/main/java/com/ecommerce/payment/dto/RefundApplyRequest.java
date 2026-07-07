package com.ecommerce.payment.dto;

import java.math.BigDecimal;

public class RefundApplyRequest {

    private Long orderId;
    private String paymentNo;
    private String refundRequestNo;
    private String reason;

    public RefundApplyRequest() {
    }

    public RefundApplyRequest(Long orderId, String paymentNo, String reason) {
        this.orderId = orderId;
        this.paymentNo = paymentNo;
        this.reason = reason;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public String getRefundRequestNo() { return refundRequestNo; }
    public void setRefundRequestNo(String refundRequestNo) { this.refundRequestNo = refundRequestNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
