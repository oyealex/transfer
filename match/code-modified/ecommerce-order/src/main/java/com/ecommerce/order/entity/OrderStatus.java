package com.ecommerce.order.entity;

/**
 * Represents the lifecycle status of an order.
 *
 * <p>Valid transitions are governed by {@link com.ecommerce.order.service.OrderStateMachine}.
 */
public enum OrderStatus {

    /** Order has been created, awaiting payment */
    CREATED,
    /** Payment is in progress */
    PAYING,
    /** Payment has been successfully completed */
    PAID,
    /** Order is being picked in warehouse */
    PICKING,
    /** Order has been shipped to customer */
    SHIPPED,
    /** Order has been delivered to customer */
    DELIVERED,
    /** Order has been completed (after return period) */
    COMPLETED,
    /** Order cancellation is under merchant review */
    CANCEL_REVIEWING,
    /** Order has been cancelled */
    CANCELLED,
    /** Refund is being processed */
    REFUNDING,
    /** Refund has been completed */
    REFUNDED,
    /** Order has been closed permanently */
    CLOSED
}
