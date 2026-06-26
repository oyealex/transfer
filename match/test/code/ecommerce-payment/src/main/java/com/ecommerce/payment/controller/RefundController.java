package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.RefundApplyRequest;
import com.ecommerce.payment.dto.RefundResponse;
import com.ecommerce.payment.service.RefundService;
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

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private static final Logger log = LoggerFactory.getLogger(RefundController.class);

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    /**
     * Applies for a refund.
     * POST /api/v1/refunds/apply -> 201 Created, USER
     */
    @PostMapping("/apply")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RefundResponse> applyRefund(@Valid @RequestBody RefundApplyRequest request) {
        Long userId = getCurrentUserId();
        log.info("Refund applied by userId={}, orderId={}", userId, request.getOrderId());
        RefundResponse response = refundService.applyRefund(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Queries a refund by ID.
     * GET /api/v1/refunds/{refundId} -> 200 OK, USER
     */
    @GetMapping("/{refundId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RefundResponse> getRefund(@PathVariable Long refundId) {
        log.info("Querying refund: refundId={}", refundId);
        RefundResponse response = refundService.getRefund(refundId);
        return ResponseEntity.ok(response);
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
