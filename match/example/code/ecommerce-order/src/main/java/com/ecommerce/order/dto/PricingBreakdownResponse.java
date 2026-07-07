package com.ecommerce.order.dto;

import java.math.BigDecimal;

/**
 * Detailed pricing breakdown response for order creation.
 * Shows every component of the order price calculation.
 */
public class PricingBreakdownResponse {

    // Item details
    private BigDecimal itemTotal;
    private int itemCount;

    // Fees
    private BigDecimal shippingFee;
    private String shippingNote;  // "Free" or "8.00"
    private BigDecimal packagingFee;

    // Discounts
    private BigDecimal fullReductionDiscount;
    private BigDecimal couponDiscount;
    private BigDecimal memberDiscount;
    private BigDecimal totalDiscount;

    // Points
    private int redeemedPoints;
    private BigDecimal pointsDeductionAmount;

    // Final
    private BigDecimal subtotalBeforeDiscount;
    private BigDecimal subtotalAfterDiscount;
    private BigDecimal payableAmount;

    // Formula display
    private String calculationFormula;
    private String calculationNote;

    public PricingBreakdownResponse() {
    }

    public BigDecimal getItemTotal() { return itemTotal; }
    public void setItemTotal(BigDecimal itemTotal) { this.itemTotal = itemTotal; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public String getShippingNote() { return shippingNote; }
    public void setShippingNote(String shippingNote) { this.shippingNote = shippingNote; }

    public BigDecimal getPackagingFee() { return packagingFee; }
    public void setPackagingFee(BigDecimal packagingFee) { this.packagingFee = packagingFee; }

    public BigDecimal getFullReductionDiscount() { return fullReductionDiscount; }
    public void setFullReductionDiscount(BigDecimal fullReductionDiscount) {
        this.fullReductionDiscount = fullReductionDiscount;
    }

    public BigDecimal getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(BigDecimal couponDiscount) { this.couponDiscount = couponDiscount; }

    public BigDecimal getMemberDiscount() { return memberDiscount; }
    public void setMemberDiscount(BigDecimal memberDiscount) { this.memberDiscount = memberDiscount; }

    public BigDecimal getTotalDiscount() { return totalDiscount; }
    public void setTotalDiscount(BigDecimal totalDiscount) { this.totalDiscount = totalDiscount; }

    public int getRedeemedPoints() { return redeemedPoints; }
    public void setRedeemedPoints(int redeemedPoints) { this.redeemedPoints = redeemedPoints; }

    public BigDecimal getPointsDeductionAmount() { return pointsDeductionAmount; }
    public void setPointsDeductionAmount(BigDecimal pointsDeductionAmount) {
        this.pointsDeductionAmount = pointsDeductionAmount;
    }

    public BigDecimal getSubtotalBeforeDiscount() { return subtotalBeforeDiscount; }
    public void setSubtotalBeforeDiscount(BigDecimal subtotalBeforeDiscount) {
        this.subtotalBeforeDiscount = subtotalBeforeDiscount;
    }

    public BigDecimal getSubtotalAfterDiscount() { return subtotalAfterDiscount; }
    public void setSubtotalAfterDiscount(BigDecimal subtotalAfterDiscount) {
        this.subtotalAfterDiscount = subtotalAfterDiscount;
    }

    public BigDecimal getPayableAmount() { return payableAmount; }
    public void setPayableAmount(BigDecimal payableAmount) { this.payableAmount = payableAmount; }

    public String getCalculationFormula() { return calculationFormula; }
    public void setCalculationFormula(String calculationFormula) {
        this.calculationFormula = calculationFormula;
    }

    public String getCalculationNote() { return calculationNote; }
    public void setCalculationNote(String calculationNote) {
        this.calculationNote = calculationNote;
    }
}
