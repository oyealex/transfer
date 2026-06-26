package com.ecommerce.order.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.query.InventoryReservationService;
import com.ecommerce.order.dto.CancelOrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.event.OrderCancelledEvent;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Handles order cancellation with different logic per order status.
 *
 * <p>Cancellation rules:
 * <ul>
 *   <li>CREATED: Direct cancel, release inventory</li>
 *   <li>PAYING: User can cancel, payment callback handles idempotency</li>
 *   <li>PAID: Cancel with refund handling</li>
 *   <li>SHIPPED: Cannot cancel, must use after-sale return</li>
 *   <li>DELIVERED: Cannot cancel, must use return/refund</li>
 * </ul>
 */
@Service
public class OrderCancelService {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelService.class);

    private final OrderRepository orderRepository;
    private final InventoryReservationService inventoryReservationService;
    private final OrderStateMachine stateMachine;
    private final DomainEventPublisher eventPublisher;
    private final OrderService orderService;

    public OrderCancelService(OrderRepository orderRepository,
                              InventoryReservationService inventoryReservationService,
                              OrderStateMachine stateMachine,
                              DomainEventPublisher eventPublisher,
                              OrderService orderService) {
        this.orderRepository = orderRepository;
        this.inventoryReservationService = inventoryReservationService;
        this.stateMachine = stateMachine;
        this.eventPublisher = eventPublisher;
        this.orderService = orderService;
    }

    /**
     * Cancel an order by user request.
     *
     * @param userId  the user ID requesting the cancellation
     * @param orderId the order ID to cancel
     * @param reason  cancellation reason
     * @return the cancel response
     */
    @Transactional
    public CancelOrderResponse cancel(Long userId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Verify ownership
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_NOT_OWNED",
                    "Order " + orderId + " does not belong to user " + userId);
        }

        OrderStatus currentStatus = order.getStatus();

        switch (currentStatus) {
            case CREATED:
                return cancelCreatedOrder(order, reason);

            case PAYING:
                return cancelPayingOrder(order, reason);

            case PAID:
                return cancelPaidOrderDirectly(order, reason);

            case SHIPPED:
            case DELIVERED:
                throw new BusinessException("ORDER_CANNOT_CANCEL",
                        "Order in status " + currentStatus + " cannot be cancelled. "
                                + "Please use the after-sale/return process.");

            case CANCELLED:
            case CLOSED:
                throw new BusinessException("ORDER_ALREADY_CANCELLED",
                        "Order is already in status " + currentStatus);

            case CANCEL_REVIEWING:
                throw new BusinessException("ORDER_CANCEL_REVIEWING",
                        "Order cancellation is already under review");

            default:
                throw new BusinessException("ORDER_CANNOT_CANCEL",
                        "Order in status " + currentStatus + " cannot be cancelled");
        }
    }

    /**
     * Cancel a CREATED order: direct cancel with inventory release.
     */
    private CancelOrderResponse cancelCreatedOrder(Order order, String reason) {
        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, OrderStatus.CANCELLED);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.save(order);

        // Release reserved inventory
        try {
            inventoryReservationService.release(order.getId());
            log.info("Inventory released for cancelled order {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to release inventory for order {}: {}", order.getId(), e.getMessage());
        }

        // Record event
        orderService.recordEvent(order.getId(), fromStatus, OrderStatus.CANCELLED,
                "CANCEL", order.getUserId().toString(), "User cancelled: " + reason);

        // Publish event
        eventPublisher.publish(new OrderCancelledEvent(this, order.getId(), order.getUserId()));

        log.info("Order {} cancelled by user {}", order.getId(), order.getUserId());
        return new CancelOrderResponse(order.getId(), OrderStatus.CANCELLED.name(),
                "Order cancelled, inventory released");
    }

    /**
     * Cancel a PAYING order: mark as cancelled. Payment callback will handle
     * idempotency if payment was already in progress.
     */
    private CancelOrderResponse cancelPayingOrder(Order order, String reason) {
        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, OrderStatus.CANCELLED);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.save(order);

        orderService.recordEvent(order.getId(), fromStatus, OrderStatus.CANCELLED,
                "CANCEL", order.getUserId().toString(),
                "User cancelled during payment: " + reason);

        eventPublisher.publish(new OrderCancelledEvent(this, order.getId(), order.getUserId()));

        log.info("PAYING order {} cancelled by user {}", order.getId(), order.getUserId());
        return new CancelOrderResponse(order.getId(), OrderStatus.CANCELLED.name(),
                "Order cancelled. If payment was processed, a refund will be issued.");
    }

    /**
     * Cancel a PAID order and calculate the refund amount.
     */
    private CancelOrderResponse cancelPaidOrderDirectly(Order order, String reason) {
        OrderStatus fromStatus = order.getStatus();

        stateMachine.validateTransition(fromStatus, OrderStatus.CANCELLED);

        // Calculate full refund (no fee deduction)
        BigDecimal refundAmount = order.getPaidAmount() != null
                ? order.getPaidAmount()
                : order.getPayableAmount();

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        order.setPaidAmount(BigDecimal.ZERO); // Full refund
        orderRepository.save(order);

        orderService.recordEvent(order.getId(), fromStatus, OrderStatus.CANCELLED,
                "CANCEL", order.getUserId().toString(),
                "User cancelled paid order directly — full refund of "
                        + refundAmount);

        eventPublisher.publish(new OrderCancelledEvent(this, order.getId(), order.getUserId()));

        log.warn("PAID order {} directly cancelled with full refund of {}",
                order.getId(), refundAmount);

        return new CancelOrderResponse(order.getId(), OrderStatus.CANCELLED.name(),
                "Order cancelled and full refund of " + refundAmount + " will be processed");
    }

    /**
     * Cancellation review flow.
     * Admin uses this via AdminOrderController.
     */
    @Transactional
    public CancelOrderResponse reviewCancel(Long orderId, boolean approved, String comment,
                                            Long reviewerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CANCEL_REVIEWING) {
            throw new BusinessException("ORDER_NOT_IN_REVIEW",
                    "Order " + orderId + " is not in CANCEL_REVIEWING status");
        }

        OrderStatus fromStatus = order.getStatus();

        if (approved) {
            // Approve cancellation: move to CANCELLED, initiate refund
            stateMachine.validateTransition(fromStatus, OrderStatus.CANCELLED);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReviewerId(reviewerId);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);

            // Release inventory if still reserved
            try {
                inventoryReservationService.release(orderId);
            } catch (Exception e) {
                log.warn("Failed to release inventory during cancel review: {}", e.getMessage());
            }

            orderService.recordEvent(orderId, fromStatus, OrderStatus.CANCELLED,
                    "CANCEL_APPROVED", reviewerId.toString(),
                    "Admin approved cancellation: " + comment);

            return new CancelOrderResponse(orderId, OrderStatus.CANCELLED.name(),
                    "Cancellation approved, refund will be processed");
        } else {
            // Reject cancellation: revert to PAID
            stateMachine.validateTransition(fromStatus, OrderStatus.PAID);
            order.setStatus(OrderStatus.PAID);
            order.setCancelReason(null);
            orderRepository.save(order);

            orderService.recordEvent(orderId, fromStatus, OrderStatus.PAID,
                    "CANCEL_REJECTED", reviewerId.toString(),
                    "Admin rejected cancellation: " + comment);

            return new CancelOrderResponse(orderId, OrderStatus.PAID.name(),
                    "Cancellation rejected, order remains active");
        }
    }
}
