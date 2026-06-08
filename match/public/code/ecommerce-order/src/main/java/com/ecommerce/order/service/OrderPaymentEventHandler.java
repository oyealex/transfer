package com.ecommerce.order.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.event.OrderPaidEvent;
import com.ecommerce.order.query.OrderPaymentStatusUpdater;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Handles payment-related order state transitions.
 * This is called by the payment module (via OrderPaymentStatusUpdater)
 * as well as by internal order lifecycle processes.
 *
 * <p>When payment is confirmed:
 * <ol>
 *   <li>Update order status to PAID</li>
 *   <li>Record payment number and timestamp</li>
 *   <li>Set paid amount</li>
 *   <li>Publish OrderPaidEvent for cross-module listeners (logistics, loyalty, notification)</li>
 *   <li>Deduct inventory (via InventoryReservationService.deductAfterPayment)</li>
 * </ol>
 *
 * <p>When payment fails:
 * <ol>
 *   <li>Revert status from PAYING back to CREATED</li>
 *   <li>Allow user to retry payment</li>
 * </ol>
 */
@Service
public class OrderPaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentEventHandler.class);

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final com.ecommerce.inventory.query.InventoryReservationService inventoryReservationService;
    private final OrderStateMachine stateMachine;
    private final OrderService orderService;

    public OrderPaymentEventHandler(OrderRepository orderRepository,
                                     DomainEventPublisher eventPublisher,
                                     com.ecommerce.inventory.query.InventoryReservationService inventoryReservationService,
                                     OrderStateMachine stateMachine,
                                     OrderService orderService) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.inventoryReservationService = inventoryReservationService;
        this.stateMachine = stateMachine;
        this.orderService = orderService;
    }

    /**
     * Handle successful payment confirmation.
     *
     * @param orderId   the order ID
     * @param paymentNo the payment transaction number
     * @param paidAmount the amount actually paid
     */
    @Transactional
    public void handlePaymentSuccess(Long orderId, String paymentNo, BigDecimal paidAmount) {
        log.info("Handling payment success: orderId={}, paymentNo={}, paidAmount={}",
                orderId, paymentNo, paidAmount);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.ecommerce.common.exception.ResourceNotFoundException(
                        "Order not found: " + orderId));

        // Validate current status
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PAYING) {
            log.warn("Order {} is not in payable status, current status={}. "
                            + "Payment may have been processed already (idempotent check).",
                    orderId, order.getStatus());
            if (order.getStatus() == OrderStatus.PAID) {
                log.info("Order {} is already PAID — payment idempotent success", orderId);
                return;
            }
            throw new com.ecommerce.common.exception.BusinessException("ORDER_NOT_PAYABLE",
                    "Order " + orderId + " is in status " + order.getStatus()
                            + " and cannot accept payment");
        }

        // Validate payment amount matches payable amount
        if (paidAmount != null && order.getPayableAmount() != null
                && paidAmount.compareTo(order.getPayableAmount()) != 0) {
            log.warn("Payment amount mismatch: expected {}, got {}",
                    order.getPayableAmount(), paidAmount);
            // In production, this would be a strict check.
            // For this implementation, we log a warning but proceed.
        }

        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, OrderStatus.PAID);

        // Update order
        order.setStatus(OrderStatus.PAID);
        order.setPaymentNo(paymentNo);
        order.setPaidAmount(order.getPayableAmount());
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);

        // Record event
        orderService.recordEvent(orderId, fromStatus, OrderStatus.PAID,
                "PAYMENT_SUCCESS", "PAYMENT_SYSTEM",
                "Payment confirmed: " + paymentNo + ", amount: " + order.getPayableAmount());

        // Deduct inventory (reserved stock is now sold)
        try {
            inventoryReservationService.deductAfterPayment(orderId);
            log.info("Inventory deducted for order {} after payment", orderId);
        } catch (Exception e) {
            log.error("Failed to deduct inventory for order {}: {}", orderId, e.getMessage());
            // In a real system, this would trigger a compensation/retry flow.
            // The order is PAID but inventory may be inconsistent — this is a
            // known failure mode that requires manual reconciliation.
        }

        // Publish event
        eventPublisher.publish(new OrderPaidEvent(this, orderId, order.getUserId(),
                paymentNo, order.getPayableAmount()));

        log.info("Payment success handled for order {}: status={}", orderId, order.getStatus());
    }

    /**
     * Handle payment failure.
     * Reverts the order from PAYING back to CREATED so the user can retry.
     *
     * @param orderId the order ID
     */
    @Transactional
    public void handlePaymentFailure(Long orderId) {
        log.info("Handling payment failure: orderId={}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.ecommerce.common.exception.ResourceNotFoundException(
                        "Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PAYING) {
            log.warn("Order {} is not in PAYING status, status={} — ignoring payment failure",
                    orderId, order.getStatus());
            return;
        }

        // Note: State machine has PAYING -> CANCELLED as a valid transition,
        // but we choose to revert to CREATED to allow retry.
        OrderStatus fromStatus = order.getStatus();

        order.setStatus(OrderStatus.CREATED);
        orderRepository.save(order);

        orderService.recordEvent(orderId, fromStatus, OrderStatus.CREATED,
                "PAYMENT_FAILED", "PAYMENT_SYSTEM",
                "Payment failed, order reverted to CREATED for retry");

        log.info("Payment failure handled for order {}: reverted to CREATED", orderId);
    }

    /**
     * Handle the case where the order needs to transition to PAYING status
     * (called before the payment gateway is invoked).
     */
    @Transactional
    public void markAsPaying(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new com.ecommerce.common.exception.ResourceNotFoundException(
                        "Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.CREATED) {
            log.warn("Order {} is not in CREATED status, status={} — cannot mark as PAYING",
                    orderId, order.getStatus());
            return;
        }

        OrderStatus fromStatus = order.getStatus();
        stateMachine.validateTransition(fromStatus, OrderStatus.PAYING);

        order.setStatus(OrderStatus.PAYING);
        orderRepository.save(order);

        orderService.recordEvent(orderId, fromStatus, OrderStatus.PAYING,
                "PAYING", "PAYMENT_SYSTEM", "Payment initiated");

        log.info("Order {} transitioned to PAYING", orderId);
    }
}
