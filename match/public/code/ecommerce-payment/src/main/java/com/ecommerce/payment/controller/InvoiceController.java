package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.InvoiceRequest;
import com.ecommerce.payment.dto.InvoiceResponse;
import com.ecommerce.payment.service.InvoiceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * Requests an invoice for an order.
     * POST /api/v1/invoices -> 201 Created, USER
     *
     * <p>Tax rate hardcoded at 0.13.
     * <p>Partial invoicing is not supported.
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        Long userId = getCurrentUserId();
        log.info("Invoice requested by userId={}, orderId={}", userId, request.getOrderId());
        InvoiceResponse response = invoiceService.generateInvoice(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets all invoices for an order.
     * GET /api/v1/invoices/order/{orderId} -> 200 OK, USER
     */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByOrder(@PathVariable Long orderId) {
        log.info("Querying invoices for orderId={}", orderId);
        List<InvoiceResponse> responses = invoiceService.getInvoicesByOrderId(orderId);
        return ResponseEntity.ok(responses);
    }

    private Long getCurrentUserId() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            return 1L; // fallback for demo
        }
    }
}
