package com.ecommerce.payment.dto;

import com.ecommerce.payment.entity.InvoiceType;

import java.math.BigDecimal;

public class InvoiceRequest {

    private Long orderId;
    private InvoiceType invoiceType;
    private BigDecimal invoiceAmount;
    private String invoiceTitle;
    private String taxId;

    public InvoiceRequest() {
    }

    public InvoiceRequest(Long orderId, InvoiceType invoiceType, BigDecimal invoiceAmount,
                          String invoiceTitle, String taxId) {
        this.orderId = orderId;
        this.invoiceType = invoiceType;
        this.invoiceAmount = invoiceAmount;
        this.invoiceTitle = invoiceTitle;
        this.taxId = taxId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public InvoiceType getInvoiceType() { return invoiceType; }
    public void setInvoiceType(InvoiceType invoiceType) { this.invoiceType = invoiceType; }
    public BigDecimal getInvoiceAmount() { return invoiceAmount; }
    public void setInvoiceAmount(BigDecimal invoiceAmount) { this.invoiceAmount = invoiceAmount; }
    public String getInvoiceTitle() { return invoiceTitle; }
    public void setInvoiceTitle(String invoiceTitle) { this.invoiceTitle = invoiceTitle; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
}
