package com.ecommerce.order.controller;

import com.ecommerce.order.dto.AdminCancelReviewRequest;
import com.ecommerce.order.dto.CancelOrderResponse;
import com.ecommerce.order.dto.SalesStatisticsRequest;
import com.ecommerce.order.dto.SalesStatisticsResponse;
import com.ecommerce.order.service.OrderCancelService;
import com.ecommerce.order.service.OrderTimeoutService;
import com.ecommerce.order.service.SalesStatisticsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin order controller for management endpoints.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderController.class);

    private final OrderCancelService orderCancelService;
    private final SalesStatisticsService salesStatisticsService;
    private final OrderTimeoutService orderTimeoutService;

    public AdminOrderController(OrderCancelService orderCancelService,
                                 SalesStatisticsService salesStatisticsService,
                                 OrderTimeoutService orderTimeoutService) {
        this.orderCancelService = orderCancelService;
        this.salesStatisticsService = salesStatisticsService;
        this.orderTimeoutService = orderTimeoutService;
    }

    /**
     * Review and approve/reject an order cancellation request.
     * Only applicable for orders in CANCEL_REVIEWING status.
     */
    @PostMapping("/{orderId}/cancel-review")
    public ResponseEntity<CancelOrderResponse> reviewCancel(
            @PathVariable Long orderId,
            @Valid @RequestBody AdminCancelReviewRequest request) {
        Long adminId = getCurrentUserId();
        boolean approved = "APPROVE".equalsIgnoreCase(request.getDecision());
        log.info("POST /api/v1/admin/orders/{}/cancel-review: adminId={}, decision={}, comment={}",
                orderId, adminId, request.getDecision(), request.getComment());

        CancelOrderResponse response = orderCancelService.reviewCancel(
                orderId, approved, request.getComment(), adminId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get sales statistics for a date range.
     */
    @GetMapping("/statistics/sales")
    public ResponseEntity<SalesStatisticsResponse> getSalesStatistics(
            @Valid SalesStatisticsRequest request) {
        log.info("GET /api/v1/admin/orders/statistics/sales: startDate={}, endDate={}",
                request.getStartDate(), request.getEndDate());
        SalesStatisticsResponse response = salesStatisticsService.getSalesStatistics(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Manually trigger timeout cancellation scan for API black-box scenarios.
     */
    @PostMapping("/timeout-cancel")
    public ResponseEntity<Map<String, Object>> triggerTimeoutCancel() {
        log.info("POST /api/v1/admin/orders/timeout-cancel");
        orderTimeoutService.cancelExpiredOrders();
        return ResponseEntity.ok(Map.of("triggered", true));
    }

    /**
     * Extracts the current admin user's ID from the Spring Security context.
     */
    private Long getCurrentUserId() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse user ID from principal '{}'", principal);
            throw new com.ecommerce.common.exception.AuthorizationException(
                    "UNAUTHORIZED", "Invalid admin principal: " + principal);
        }
    }
}
