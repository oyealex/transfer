package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.dto.PayResponse;
import com.ecommerce.payment.dto.PaymentCallbackRequest;
import com.ecommerce.payment.service.PaymentCallbackService;
import com.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final PaymentCallbackService paymentCallbackService;

    public PaymentController(PaymentService paymentService,
                             PaymentCallbackService paymentCallbackService) {
        this.paymentService = paymentService;
        this.paymentCallbackService = paymentCallbackService;
    }

    /**
     * Initiates a payment for an order.
     * POST /api/v1/payment/pay -> 201 Created
     */
    @PostMapping("/pay")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PayResponse> pay(@Valid @RequestBody PayRequest request) {
        log.info("Payment initiated for orderId={}", request.getOrderId());
        PayResponse response = paymentService.pay(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Payment gateway callback.
     * POST /api/v1/payment/callback -> 200 OK (authenticated via signature, not JWT)
     */
    @PostMapping("/callback")
    public ResponseEntity<String> callback(@RequestBody PaymentCallbackRequest request,
                                           @RequestHeader(value = "X-Payment-Signature", required = false) String signatureHeader) {
        log.info("Payment callback received: paymentNo={}, status={}",
                request.getPaymentNo(), request.getStatus());
        // Populate signature from header if not in body
        if (request.getSignature() == null && signatureHeader != null) {
            request.setSignature(signatureHeader);
        }
        paymentCallbackService.processCallback(request);
        return ResponseEntity.ok("OK");
    }

    /**
     * Retrieves a payment by payment number.
     * GET /api/v1/payment/{paymentNo} -> 200 OK
     */
    @GetMapping("/{paymentNo}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PayResponse> getPayment(@PathVariable String paymentNo) {
        log.info("Querying payment: paymentNo={}", paymentNo);
        PayResponse response = paymentService.getPayment(paymentNo);
        return ResponseEntity.ok(response);
    }
}
