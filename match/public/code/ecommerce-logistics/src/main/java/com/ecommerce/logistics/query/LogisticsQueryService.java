package com.ecommerce.logistics.query;

import com.ecommerce.logistics.dto.ShipmentResponse;

/**
 * Cross-module query interface exposed by the logistics module.
 * Other modules use this interface to query logistics data without
 * depending on logistics JPA entities or repositories.
 */
public interface LogisticsQueryService {

    /**
     * Query the shipment for an order.
     *
     * @param orderId the order ID
     * @return the shipment response, or null if no shipment exists
     */
    ShipmentResponse getShipmentByOrderId(Long orderId);

    /**
     * Query the latest tracking status for a shipment.
     *
     * @param shipmentId the shipment ID
     * @return the current status string
     */
    String getCurrentStatus(Long shipmentId);
}
