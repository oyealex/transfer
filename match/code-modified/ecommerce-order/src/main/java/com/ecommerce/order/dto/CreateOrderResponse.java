package com.ecommerce.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

/**
 * Response DTO returned after successfully creating an order.
 */
public class CreateOrderResponse {

    private Long orderId;
    private String orderNo;
    private String status;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal itemTotal;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal shippingFee;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal packagingFee;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal discountAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal pointsDeductionAmount;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal payableAmount;

    public CreateOrderResponse() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
}
