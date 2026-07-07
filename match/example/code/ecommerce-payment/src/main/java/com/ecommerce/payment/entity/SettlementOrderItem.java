package com.ecommerce.payment.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "settlement_order_items", indexes = {
        @Index(name = "idx_settlement_item_batch_id", columnList = "batchId"),
        @Index(name = "idx_settlement_item_order_id", columnList = "orderId")
})
public class SettlementOrderItem extends BaseEntity {

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 64)
    private String paymentNo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount;

    private Long invoiceId;

    @Column(precision = 12, scale = 2)
    private BigDecimal invoiceAmount;

    public SettlementOrderItem() {
    }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public BigDecimal getInvoiceAmount() { return invoiceAmount; }
    public void setInvoiceAmount(BigDecimal invoiceAmount) { this.invoiceAmount = invoiceAmount; }
}
