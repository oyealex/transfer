package com.ecommerce.payment.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.payment.dto.InvoiceRequest;
import com.ecommerce.payment.dto.InvoiceResponse;
import com.ecommerce.payment.entity.InvoiceRecord;
import com.ecommerce.payment.entity.InvoiceStatus;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.InvoiceRecordRepository;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles invoice generation for paid orders.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private static final BigDecimal TAX_RATE = new BigDecimal("0.06");

    private final InvoiceRecordRepository invoiceRecordRepository;
    private final PaymentRecordRepository paymentRecordRepository;

    public InvoiceService(InvoiceRecordRepository invoiceRecordRepository,
                          PaymentRecordRepository paymentRecordRepository) {
        this.invoiceRecordRepository = invoiceRecordRepository;
        this.paymentRecordRepository = paymentRecordRepository;
    }

    /**
     * Generates an invoice for an order. Supports partial invoicing:
     * the invoice amount is taken from the request, allowing multiple
     * invoices against the same order as long as the total invoiced
     * does not exceed the paid amount.
     */
    @Transactional
    public InvoiceResponse generateInvoice(Long userId, InvoiceRequest request) {
        log.info("Generating invoice: userId={}, orderId={}, type={}, requestedAmount={}",
                userId, request.getOrderId(), request.getInvoiceType(), request.getInvoiceAmount());

        // Find the successful payment for this order
        List<PaymentRecord> payments = paymentRecordRepository.findByOrderId(request.getOrderId());
        PaymentRecord successfulPayment = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .findFirst()
                .orElseThrow(() -> new BusinessException("NO_PAID_PAYMENT",
                        "Order " + request.getOrderId() + " has no successful payment"));

        BigDecimal invoiceAmount = request.getInvoiceAmount();

        // Validate invoiceAmount > 0
        if (invoiceAmount == null || invoiceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("invoiceAmount",
                    "Invoice amount must be greater than 0, got: " + invoiceAmount);
        }

        // Check remaining invoiceable amount
        BigDecimal alreadyInvoiced = invoiceRecordRepository
                .sumInvoiceAmountByOrderIdAndStatus(request.getOrderId(), InvoiceStatus.ISSUED);
        BigDecimal remaining = MonetaryUtil.subtract(
                successfulPayment.getPaidAmount(), alreadyInvoiced);

        if (alreadyInvoiced.compareTo(successfulPayment.getPaidAmount()) >= 0) {
            throw new BusinessException("INVOICE_LIMIT_EXCEEDED",
                    "Order " + request.getOrderId() + " has already been fully invoiced");
        }

        // Check that the requested invoice amount does not exceed remaining
        if (invoiceAmount.compareTo(remaining) > 0) {
            throw new BusinessException("INVOICE_AMOUNT_EXCEEDED",
                    "Invoice amount " + invoiceAmount + " exceeds remaining invoiceable amount " + remaining);
        }

        BigDecimal taxRate = com.ecommerce.common.test.RuntimeConfigRegistry
                .getBigDecimal("invoice.tax-rate", TAX_RATE);
        BigDecimal taxAmount = MonetaryUtil.roundToCent(
                invoiceAmount.multiply(taxRate).setScale(4, RoundingMode.HALF_UP));

        // Compute new remaining after this invoice
        BigDecimal newRemaining = MonetaryUtil.subtract(remaining, invoiceAmount);

        InvoiceRecord invoice = new InvoiceRecord();
        invoice.setInvoiceNo(generateInvoiceNo());
        invoice.setOrderId(request.getOrderId());
        invoice.setUserId(userId);
        invoice.setInvoiceType(request.getInvoiceType());
        invoice.setInvoiceAmount(invoiceAmount);
        invoice.setTaxRate(taxRate);
        invoice.setTaxAmount(taxAmount);
        invoice.setRemainingInvoiceableAmount(newRemaining);
        invoice.setInvoiceTitle(request.getInvoiceTitle());
        invoice.setTaxId(request.getTaxId());
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(LocalDateTime.now());

        invoice = invoiceRecordRepository.save(invoice);

        log.info("Invoice generated: invoiceNo={}, amount={}, taxAmount={}, remaining={}",
                invoice.getInvoiceNo(), invoice.getInvoiceAmount(), invoice.getTaxAmount(),
                invoice.getRemainingInvoiceableAmount());

        return toInvoiceResponse(invoice);
    }

    /**
     * Gets all invoices for an order.
     */
    public List<InvoiceResponse> getInvoicesByOrderId(Long orderId) {
        List<InvoiceRecord> invoices = invoiceRecordRepository.findByOrderId(orderId);
        return invoices.stream()
                .map(this::toInvoiceResponse)
                .collect(Collectors.toList());
    }

    private String generateInvoiceNo() {
        return "INV" + System.currentTimeMillis() + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private InvoiceResponse toInvoiceResponse(InvoiceRecord invoice) {
        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setInvoiceNo(invoice.getInvoiceNo());
        response.setOrderId(invoice.getOrderId());
        response.setUserId(invoice.getUserId());
        response.setInvoiceType(invoice.getInvoiceType());
        response.setInvoiceAmount(invoice.getInvoiceAmount());
        response.setTaxRate(invoice.getTaxRate());
        response.setTaxAmount(invoice.getTaxAmount());
        response.setRemainingInvoiceableAmount(invoice.getRemainingInvoiceableAmount());
        response.setInvoiceTitle(invoice.getInvoiceTitle());
        response.setTaxId(invoice.getTaxId());
        response.setStatus(invoice.getStatus());
        response.setIssuedAt(invoice.getIssuedAt());
        response.setCreatedAt(invoice.getCreatedAt());
        return response;
    }
}
