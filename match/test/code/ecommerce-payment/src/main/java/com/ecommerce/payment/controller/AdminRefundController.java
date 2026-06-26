package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.RefundResponse;
import com.ecommerce.payment.dto.RefundReviewRequest;
import com.ecommerce.payment.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private static final Logger log = LoggerFactory.getLogger(AdminRefundController.class);

    private final RefundService refundService;

    public AdminRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    /**
     * Admin reviews a refund application.
     * POST /api/v1/admin/refunds/{refundId}/review -> 200 OK, ADMIN
     */
    @PostMapping("/{refundId}/review")
    public ResponseEntity<RefundResponse> reviewRefund(
            @PathVariable Long refundId,
            @RequestBody RefundReviewRequest request) {
        Long adminId = getCurrentAdminId();
        log.info("Admin {} reviewing refund: refundId={}, approved={}",
                adminId, refundId, request.isApproved());
        RefundResponse response = refundService.reviewRefund(refundId, adminId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Warehouse accepts returned goods.
     * POST /api/v1/admin/refunds/{refundId}/warehouse-accept -> 200 OK, ADMIN
     */
    @PostMapping("/{refundId}/warehouse-accept")
    public ResponseEntity<RefundResponse> warehouseAccept(@PathVariable Long refundId) {
        Long acceptorId = getCurrentAdminId();
        log.info("Warehouse accepting refund: refundId={}, acceptorId={}", refundId, acceptorId);
        RefundResponse response = refundService.warehouseAccept(refundId, acceptorId);
        return ResponseEntity.ok(response);
    }

    private Long getCurrentAdminId() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
