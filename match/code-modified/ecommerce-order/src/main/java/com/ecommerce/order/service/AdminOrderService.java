package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-level order management operations.
 * Provides capabilities beyond the user-facing OrderService, including
 * manual status transitions, order search by any field, and bulk operations.
 */
@Service
@Transactional
public class AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderAssembler orderAssembler;
    private final OrderStateMachine stateMachine;
    private final OrderService orderService;

    public AdminOrderService(OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              OrderAssembler orderAssembler,
                              OrderStateMachine stateMachine,
                              OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderAssembler = orderAssembler;
        this.stateMachine = stateMachine;
        this.orderService = orderService;
    }

    /**
     * Get any order detail regardless of ownership (admin only).
     */
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return orderAssembler.toDetailResponse(order,
                orderItemRepository.findByOrderId(orderId), List.of());
    }

    /**
     * Get order by order number (admin tool).
     */
    @Transactional(readOnly = true)
    public Order getByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNo));
    }

    /**
     * List all orders with pagination (admin only).
     */
    @Transactional(readOnly = true)
    public Page<Order> listAllOrders(int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.findAll(pageable);
    }

    /**
     * Find orders by status (admin only).
     */
    @Transactional(readOnly = true)
    public List<Order> findByStatus(String status) {
        // In a real system, this would be a repository query.
        // Here we filter from all orders for demonstration.
        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_ORDER_STATUS",
                    "Unknown order status: " + status);
        }

        return orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == orderStatus)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Manually mark an order as PICKING (admin operation).
     * Used when the warehouse starts processing a paid order.
     */
    public void markAsPicking(Long orderId, Long operatorId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, OrderStatus.PICKING);

        order.setStatus(OrderStatus.PICKING);
        orderRepository.save(order);

        orderService.recordEvent(orderId, fromStatus, OrderStatus.PICKING,
                "PICKING", operatorId.toString(), "Admin marked order as picking");

        log.info("Order {} marked as PICKING by admin {}", orderId, operatorId);
    }

    /**
     * Manually mark an order as SHIPPED (admin operation).
     */
    public void markAsShipped(Long orderId, Long operatorId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, OrderStatus.SHIPPED);

        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);

        orderService.recordEvent(orderId, fromStatus, OrderStatus.SHIPPED,
                "SHIPPED", operatorId.toString(), "Admin marked order as shipped");

        log.info("Order {} marked as SHIPPED by admin {}", orderId, operatorId);
    }

    /**
     * Manually mark an order as DELIVERED (admin operation).
     */
    public void markAsDelivered(Long orderId, Long operatorId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, OrderStatus.DELIVERED);

        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        orderService.recordEvent(orderId, fromStatus, OrderStatus.DELIVERED,
                "DELIVERED", operatorId.toString(), "Admin confirmed delivery");

        log.info("Order {} marked as DELIVERED by admin {}", orderId, operatorId);
    }

    /**
     * Manually mark an order as COMPLETED (admin operation).
     */
    public void markAsCompleted(Long orderId, Long operatorId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, OrderStatus.COMPLETED);

        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        orderService.recordEvent(orderId, fromStatus, OrderStatus.COMPLETED,
                "COMPLETED", operatorId.toString(), "Admin completed order after return period");

        log.info("Order {} marked as COMPLETED by admin {}", orderId, operatorId);
    }

    /**
     * Manually mark an order as CLOSED (admin operation).
     * Only allowed from CANCELLED or COMPLETED status.
     */
    public void markAsClosed(Long orderId, Long operatorId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, OrderStatus.CLOSED);

        order.setStatus(OrderStatus.CLOSED);
        order.setCancelReason(reason);
        orderRepository.save(order);

        orderService.recordEvent(orderId, fromStatus, OrderStatus.CLOSED,
                "CLOSED", operatorId.toString(), "Admin closed order: " + reason);

        log.info("Order {} marked as CLOSED by admin {}: {}", orderId, operatorId, reason);
    }

    /**
     * Count orders by status for dashboard summary.
     */
    @Transactional(readOnly = true)
    public long countByStatus(OrderStatus status) {
        return orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == status)
                .count();
    }

    /**
     * Get dashboard summary with counts for key statuses.
     */
    @Transactional(readOnly = true)
    public OrderDashboardSummary getDashboardSummary() {
        OrderDashboardSummary summary = new OrderDashboardSummary();
        summary.setPendingPayment(countByStatus(OrderStatus.CREATED) + countByStatus(OrderStatus.PAYING));
        summary.setPaid(countByStatus(OrderStatus.PAID));
        summary.setProcessing(countByStatus(OrderStatus.PICKING) + countByStatus(OrderStatus.SHIPPED));
        summary.setDelivered(countByStatus(OrderStatus.DELIVERED));
        summary.setCompleted(countByStatus(OrderStatus.COMPLETED));
        summary.setCancelReviewing(countByStatus(OrderStatus.CANCEL_REVIEWING));
        summary.setCancelled(countByStatus(OrderStatus.CANCELLED));
        summary.setRefunding(countByStatus(OrderStatus.REFUNDING));
        summary.setTimestamp(LocalDateTime.now());
        return summary;
    }

    /**
     * Dashboard summary DTO for admin overview.
     */
    public static class OrderDashboardSummary {
        private long pendingPayment;
        private long paid;
        private long processing;
        private long delivered;
        private long completed;
        private long cancelReviewing;
        private long cancelled;
        private long refunding;
        private LocalDateTime timestamp;

        public long getPendingPayment() { return pendingPayment; }
        public void setPendingPayment(long pendingPayment) { this.pendingPayment = pendingPayment; }
        public long getPaid() { return paid; }
        public void setPaid(long paid) { this.paid = paid; }
        public long getProcessing() { return processing; }
        public void setProcessing(long processing) { this.processing = processing; }
        public long getDelivered() { return delivered; }
        public void setDelivered(long delivered) { this.delivered = delivered; }
        public long getCompleted() { return completed; }
        public void setCompleted(long completed) { this.completed = completed; }
        public long getCancelReviewing() { return cancelReviewing; }
        public void setCancelReviewing(long cancelReviewing) { this.cancelReviewing = cancelReviewing; }
        public long getCancelled() { return cancelled; }
        public void setCancelled(long cancelled) { this.cancelled = cancelled; }
        public long getRefunding() { return refunding; }
        public void setRefunding(long refunding) { this.refunding = refunding; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}
