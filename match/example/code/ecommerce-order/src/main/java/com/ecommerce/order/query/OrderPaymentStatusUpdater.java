package com.ecommerce.order.query;

/**
 * Cross-module interface for updating order payment status.
 * Used by the payment module to notify order status changes after payment processing.
 *
 * <p>This interface is implemented by the order module and injected into the payment module.
 */
public interface OrderPaymentStatusUpdater {

    /**
     * Mark an order as paid after payment confirmation.
     *
     * @param orderId   the order ID
     * @param paymentNo the payment transaction number
     */
    void markAsPaid(Long orderId, String paymentNo);

    /**
     * Mark an order as having payment failed.
     *
     * @param orderId the order ID
     */
    void markPaymentFailed(Long orderId);
}
