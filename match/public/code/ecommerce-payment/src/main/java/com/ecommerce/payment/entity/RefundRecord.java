package com.ecommerce.payment.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_records", indexes = {
        @Index(name = "idx_refund_no", columnList = "refundNo", unique = true),
        @Index(name = "idx_refund_payment_no", columnList = "paymentNo"),
        @Index(name = "idx_refund_order_id", columnList = "orderId"),
        @Index(name = "idx_refund_user_id", columnList = "userId"),
        @Index(name = "idx_refund_status", columnList = "status")
})
public class RefundRecord extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String refundNo;

    @Column(nullable = false, length = 64)
    private String paymentNo;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    private Long reviewerId;

    private Long warehouseAcceptorId;

    @Column(length = 500)
    private String reviewNote;

    private LocalDateTime completedAt;

    public RefundRecord() {
    }

    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus status) { this.status = status; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Long getWarehouseAcceptorId() { return warehouseAcceptorId; }
    public void setWarehouseAcceptorId(Long warehouseAcceptorId) { this.warehouseAcceptorId = warehouseAcceptorId; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
