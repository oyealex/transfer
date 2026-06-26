package com.ecommerce.logistics.query;

/**
 * Cross-module interface for updating the logistics status on an order.
 * Implemented by the order module and injected into the logistics module.
 *
 * <p>After the logistics module changes a shipment's status, it must call
 * this updater to keep the order's logistics status in sync.
 */
public interface OrderLogisticsStatusUpdater {

    /**
     * Update the logistics status of an order.
     *
     * @param orderId         the order ID
     * @param logisticsStatus the new logistics status
     */
    void updateLogisticsStatus(Long orderId, String logisticsStatus);
}
