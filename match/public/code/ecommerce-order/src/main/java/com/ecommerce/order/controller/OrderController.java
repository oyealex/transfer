package com.ecommerce.order.controller;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.order.dto.BatchCreateOrderRequest;
import com.ecommerce.order.dto.BatchCreateOrderResponse;
import com.ecommerce.order.dto.CancelOrderResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.CreateOrderResponse;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderListResponse;
import com.ecommerce.order.dto.VerifyPurchaseRequest;
import com.ecommerce.order.dto.VerifyPurchaseResponse;
import com.ecommerce.order.service.BatchOrderService;
import com.ecommerce.order.service.OrderCancelService;
import com.ecommerce.order.service.OrderService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order REST controller for user-facing endpoints.
 * All endpoints require USER role.
 */
@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('USER')")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final OrderCancelService orderCancelService;
    private final BatchOrderService batchOrderService;

    public OrderController(OrderService orderService,
                           OrderCancelService orderCancelService,
                           BatchOrderService batchOrderService) {
        this.orderService = orderService;
        this.orderCancelService = orderCancelService;
        this.batchOrderService = batchOrderService;
    }

    /**
     * Create a new order.
     */
    @PostMapping("/create")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        Long userId = getCurrentUserId();
        log.info("POST /api/v1/orders/create: userId={}, itemsCount={}",
                userId, request.getItems() != null ? request.getItems().size() : 0);
        CreateOrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get order detail by order ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(@PathVariable Long orderId) {
        log.info("GET /api/v1/orders/{}", orderId);
        OrderDetailResponse response = orderService.getOrderDetail(orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * List orders for the current user, paginated.
     */
    @GetMapping
    public ResponseEntity<PageResponse<OrderListResponse>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = getCurrentUserId();
        log.info("GET /api/v1/orders: userId={}, page={}, size={}", userId, page, size);
        var orderPage = orderService.listUserOrders(userId, page, size);
        PageResponse<OrderListResponse> response = PageResponse.of(
                page, size, orderPage.getTotalElements(), orderPage.getContent());
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel an order.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CancelOrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false, defaultValue = "User requested cancellation") String reason) {
        Long userId = getCurrentUserId();
        log.info("POST /api/v1/orders/{}/cancel: userId={}, reason={}", orderId, userId, reason);
        CancelOrderResponse response = orderCancelService.cancel(userId, orderId, reason);
        return ResponseEntity.ok(response);
    }

    /**
     * Batch create orders.
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchCreateOrderResponse> createBatch(
            @Valid @RequestBody BatchCreateOrderRequest request) {
        Long userId = getCurrentUserId();
        log.info("POST /api/v1/orders/batch: userId={}, count={}, continueOnError={}",
                userId, request.getOrders().size(), request.isContinueOnError());
        BatchCreateOrderResponse response = batchOrderService.createBatch(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify purchase: check if a user has purchased and received a product.
     */
    @GetMapping("/verify-purchase")
    public ResponseEntity<VerifyPurchaseResponse> verifyPurchase(
            @Valid VerifyPurchaseRequest request) {
        log.info("GET /api/v1/orders/verify-purchase: userId={}, productId={}",
                request.getUserId(), request.getProductId());
        VerifyPurchaseResponse response = orderService.verifyPurchase(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Extracts the current user's ID from the Spring Security context.
     */
    private Long getCurrentUserId() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse user ID from principal '{}'", principal);
            throw new com.ecommerce.common.exception.AuthorizationException(
                    "UNAUTHORIZED", "Invalid user principal: " + principal);
        }
    }
}
