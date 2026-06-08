package com.ecommerce.order.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core order entity.
 *
 * <p>Order number format: SO + yyyyMMdd + sequence (e.g., SO202606070001).
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_order_no", columnList = "orderNo", unique = true),
        @Index(name = "idx_orders_user_id", columnList = "userId"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_status_expires", columnList = "status,expiresAt")
})
public class Order extends BaseEntity {

    /** Unique order number, format: SO+yyyyMMdd+4-digit sequence */
    @Column(nullable = false, unique = true, length = 32)
    private String orderNo;

    /** The user who placed this order */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** External order number from client system */
    @Column(name = "external_order_no", length = 128)
    private String externalOrderNo;

    /** Current order status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    /** Sum of (item price * quantity) for all order items */
    @Column(name = "item_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal itemTotal;

    /** Shipping fee for the order */
    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee;

    /** Packaging fee for the order */
    @Column(name = "packaging_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal packagingFee;

    /** Total discount amount (promotions + coupons + member) */
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    /** Amount deducted via loyalty points redemption */
    @Column(name = "points_deduction_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal pointsDeductionAmount;

    /** Final amount the customer must pay */
    @Column(name = "payable_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal payableAmount;

    /** Amount actually paid (set after payment confirmation) */
    @Column(name = "paid_amount", precision = 12, scale = 2)
    private BigDecimal paidAmount;

    /** JSON snapshot of the shipping address at order time */
    @Column(name = "address_snapshot", columnDefinition = "TEXT")
    private String addressSnapshot;

    /** Comma-separated list of coupon IDs applied */
    @Column(name = "coupon_ids", columnDefinition = "TEXT")
    private String couponIds;

    /** Number of loyalty points redeemed */
    @Column(name = "redeemed_points")
    private int redeemedPoints;

    /** Payment transaction number (set after payment) */
    @Column(name = "payment_no", length = 64)
    private String paymentNo;

    /** Reason for cancellation */
    @Column(name = "cancel_reason", length = 512)
    private String cancelReason;

    /** Admin user ID who reviewed the cancellation */
    @Column(name = "cancel_reviewer_id")
    private Long cancelReviewerId;

    /** When the order was paid */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** When the order was cancelled */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Order expiry time (createdAt + 60 minutes), after which it auto-cancels */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public Order() {
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getExternalOrderNo() {
        return externalOrderNo;
    }

    public void setExternalOrderNo(String externalOrderNo) {
        this.externalOrderNo = externalOrderNo;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getItemTotal() {
        return itemTotal;
    }

    public void setItemTotal(BigDecimal itemTotal) {
        this.itemTotal = itemTotal;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public BigDecimal getPackagingFee() {
        return packagingFee;
    }

    public void setPackagingFee(BigDecimal packagingFee) {
        this.packagingFee = packagingFee;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getPointsDeductionAmount() {
        return pointsDeductionAmount;
    }

    public void setPointsDeductionAmount(BigDecimal pointsDeductionAmount) {
        this.pointsDeductionAmount = pointsDeductionAmount;
    }

    public BigDecimal getPayableAmount() {
        return payableAmount;
    }

    public void setPayableAmount(BigDecimal payableAmount) {
        this.payableAmount = payableAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getAddressSnapshot() {
        return addressSnapshot;
    }

    public void setAddressSnapshot(String addressSnapshot) {
        this.addressSnapshot = addressSnapshot;
    }

    public String getCouponIds() {
        return couponIds;
    }

    public void setCouponIds(String couponIds) {
        this.couponIds = couponIds;
    }

    public int getRedeemedPoints() {
        return redeemedPoints;
    }

    public void setRedeemedPoints(int redeemedPoints) {
        this.redeemedPoints = redeemedPoints;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Long getCancelReviewerId() {
        return cancelReviewerId;
    }

    public void setCancelReviewerId(Long cancelReviewerId) {
        this.cancelReviewerId = cancelReviewerId;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
