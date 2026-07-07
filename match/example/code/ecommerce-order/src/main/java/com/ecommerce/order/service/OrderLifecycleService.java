package com.ecommerce.order.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.event.OrderCancelledEvent;
import com.ecommerce.order.event.OrderPaidEvent;
import com.ecommerce.order.integration.InventoryIntegrationService;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages the full lifecycle of an order across all status transitions.
 *
 * <p>This service is the single entry point for order status changes,
 * ensuring consistency across:
 * <ul>
 *   <li>Status transition validation via OrderStateMachine</li>
 *   <li>Inventory operations (reserve, release, deduct)</li>
 *   <li>Event publishing for cross-module notification</li>
 *   <li>Audit logging via OrderEventLog</li>
 *   <li>Timestamps (paidAt, cancelledAt, etc.)</li>
 * </ul>
 *
 * <p>Every status change in the system should go through this service
 * to ensure all side effects are properly managed.
 */
@Service
public class OrderLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleService.class);

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final DomainEventPublisher eventPublisher;
    private final InventoryIntegrationService inventoryIntegration;
    private final OrderEventLogService eventLogService;
    private final OrderService orderService;

    public OrderLifecycleService(OrderRepository orderRepository,
                                  OrderStateMachine stateMachine,
                                  DomainEventPublisher eventPublisher,
                                  InventoryIntegrationService inventoryIntegration,
                                  OrderEventLogService eventLogService,
                                  OrderService orderService) {
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.inventoryIntegration = inventoryIntegration;
        this.eventLogService = eventLogService;
        this.orderService = orderService;
    }

    /**
     * Transition an order from one status to another.
     * This is the single method for all status transitions in the system.
     *
     * @param orderId    the order ID
     * @param targetStatus the desired status
     * @param eventType  the event type for audit logging
     * @param operatorId the operator (user ID or "SYSTEM")
     * @param note       human-readable note
     * @return the updated order
     */
    @Transactional
    public Order transition(Long orderId, OrderStatus targetStatus,
                             String eventType, String operatorId, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.ecommerce.common.exception.ResourceNotFoundException(
                        "Order not found: " + orderId));

        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, targetStatus);

        // Apply status change
        order.setStatus(targetStatus);

        // Handle status-specific side effects
        handleStatusTransition(order, fromStatus, targetStatus, operatorId);

        orderRepository.save(order);

        // Record audit event
        eventLogService.recordEvent(orderId, fromStatus, targetStatus,
                eventType, operatorId, note);

        // Publish domain events based on status
        publishTransitionEvent(order, fromStatus, targetStatus);

        log.info("Order {} transitioned: {} -> {} (by {})",
                orderId, fromStatus, targetStatus, operatorId);

        return order;
    }

    /**
     * Handle status-specific side effects like timestamp updates and inventory ops.
     */
    private void handleStatusTransition(Order order, OrderStatus from,
                                         OrderStatus to, String operatorId) {
        switch (to) {
            case PAID:
                // Deduct inventory after payment
                try {
                    inventoryIntegration.deductInventory(order.getId());
                } catch (Exception e) {
                    log.error("Failed to deduct inventory during PAID transition for order {}: {}",
                            order.getId(), e.getMessage());
                }
                break;

            case CANCELLED:
                order.setCancelledAt(LocalDateTime.now());
                // Release inventory if it was reserved
                try {
                    inventoryIntegration.releaseInventoryIfCancellable(order.getId());
                } catch (Exception e) {
                    log.error("Failed to release inventory during CANCELLED transition for order {}: {}",
                            order.getId(), e.getMessage());
                }
                break;

            case SHIPPED:
                // Shipping timestamp managed by logistics module
                break;

            case DELIVERED:
                // Delivery confirmation
                break;

            case COMPLETED:
                // Order completion — after return period
                break;

            case CLOSED:
                // Final closure
                break;

            default:
                break;
        }
    }

    /**
     * Publish the appropriate domain event for a status transition.
     */
    private void publishTransitionEvent(Order order, OrderStatus from, OrderStatus to) {
        switch (to) {
            case PAYING:
                // Payment is just starting — no event yet
                break;

            case PAID:
                eventPublisher.publish(new OrderPaidEvent(this, order.getId(),
                        order.getUserId(), order.getPaymentNo(), order.getPayableAmount()));
                break;

            case CANCELLED:
                eventPublisher.publish(new OrderCancelledEvent(this, order.getId(),
                        order.getUserId()));
                break;

            default:
                break;
        }
    }

    /**
     * Batch transition multiple orders to the same status.
     * Each order is processed independently — a failure in one does not
     * prevent others from being processed.
     *
     * @param orderIds    list of order IDs
     * @param targetStatus desired status
     * @param eventType   event type
     * @param operatorId  operator
     * @param note        note
     * @return number of successfully transitioned orders
     */
    @Transactional
    public int batchTransition(List<Long> orderIds, OrderStatus targetStatus,
                                String eventType, String operatorId, String note) {
        int successCount = 0;
        for (Long orderId : orderIds) {
            try {
                transition(orderId, targetStatus, eventType, operatorId, note);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to transition order {} to {}: {}",
                        orderId, targetStatus, e.getMessage());
            }
        }
        log.info("Batch transition: {}/{} orders transitioned to {}",
                successCount, orderIds.size(), targetStatus);
        return successCount;
    }

    /**
     * Check if an order is in a terminal state (no further transitions possible).
     */
    public boolean isTerminal(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return true;
        return order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.CLOSED
                || order.getStatus() == OrderStatus.REFUNDED;
    }

    /**
     * Check if an order can be modified (not terminal or locked).
     */
    public boolean isModifiable(Long orderId) {
        return !isTerminal(orderId);
    }

    /**
     * Get the current status of an order quickly.
     */
    public OrderStatus getCurrentStatus(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        return order != null ? order.getStatus() : null;
    }

    /**
     * Reset an order to CREATED status (admin override for stuck orders).
     * Should only be used for reconciliation purposes.
     */
    @Transactional
    public void resetToCreated(Long orderId, Long adminId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.ecommerce.common.exception.ResourceNotFoundException(
                        "Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.CLOSED) {
            throw new com.ecommerce.common.exception.BusinessException(
                    "ORDER_TERMINAL",
                    "Cannot reset terminal order " + orderId);
        }

        OrderStatus fromStatus = order.getStatus();
        order.setStatus(OrderStatus.CREATED);
        orderRepository.save(order);

        eventLogService.recordEvent(orderId, fromStatus, OrderStatus.CREATED,
                "ADMIN_RESET", adminId.toString(),
                "Admin reset order to CREATED for reconciliation");

        log.warn("Order {} reset to CREATED by admin {} (from {})",
                orderId, adminId, fromStatus);
    }
}
