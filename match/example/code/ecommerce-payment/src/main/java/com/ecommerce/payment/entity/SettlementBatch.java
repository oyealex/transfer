package com.ecommerce.payment.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_batches", indexes = {
        @Index(name = "idx_settlement_batch_no", columnList = "batchNo", unique = true),
        @Index(name = "idx_settlement_batch_date", columnList = "batchDate")
})
public class SettlementBatch extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String batchNo;

    @Column(nullable = false)
    private LocalDate batchDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPaymentAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalRefundAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalInvoiceAmount;

    @Column(nullable = false)
    private Integer orderCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    private LocalDateTime generatedAt;

    public SettlementBatch() {
    }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public LocalDate getBatchDate() { return batchDate; }
    public void setBatchDate(LocalDate batchDate) { this.batchDate = batchDate; }
    public BigDecimal getTotalPaymentAmount() { return totalPaymentAmount; }
    public void setTotalPaymentAmount(BigDecimal totalPaymentAmount) { this.totalPaymentAmount = totalPaymentAmount; }
    public BigDecimal getTotalRefundAmount() { return totalRefundAmount; }
    public void setTotalRefundAmount(BigDecimal totalRefundAmount) { this.totalRefundAmount = totalRefundAmount; }
    public BigDecimal getTotalInvoiceAmount() { return totalInvoiceAmount; }
    public void setTotalInvoiceAmount(BigDecimal totalInvoiceAmount) { this.totalInvoiceAmount = totalInvoiceAmount; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus status) { this.status = status; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
