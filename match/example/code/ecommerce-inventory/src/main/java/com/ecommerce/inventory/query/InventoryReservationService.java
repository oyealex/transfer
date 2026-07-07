package com.ecommerce.inventory.query;

import java.util.List;

/**
 * Cross-module interface for inventory reservation operations.
 * The order module uses this interface to reserve, release, and deduct stock
 * during the order lifecycle.
 */
public interface InventoryReservationService {

    /**
     * Reserves stock for the given order items.
     * Called when an order is created.
     *
     * @param orderId the order id
     * @param items   the items to reserve
     */
    void reserve(Long orderId, List<ReserveItem> items);

    /**
     * Releases all stock reservations for the given order.
     * Called when an order is cancelled or times out.
     *
     * @param orderId the order id
     */
    void release(Long orderId);

    /**
     * Deducts reserved stock after payment is confirmed.
     * Decreases both on-hand stock and reserved stock, and creates outbound orders.
     *
     * @param orderId the order id
     */
    void deductAfterPayment(Long orderId);
}
