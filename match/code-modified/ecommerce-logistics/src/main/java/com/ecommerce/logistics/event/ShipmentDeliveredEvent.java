package com.ecommerce.logistics.event;

import com.ecommerce.common.event.AbstractDomainEvent;

import java.time.LocalDateTime;

/**
 * Published by logistics-service when a shipment is delivered.
 * Listened to by order-service (to update order status) and loyalty-service (to award points).
 */
public class ShipmentDeliveredEvent extends AbstractDomainEvent {

    private final Long orderId;
    private final Long shipmentId;
    private final LocalDateTime deliveredAt;

    public ShipmentDeliveredEvent(Object source, Long orderId, Long shipmentId,
                                   LocalDateTime deliveredAt) {
        super(source);
        this.orderId = orderId;
        this.shipmentId = shipmentId;
        this.deliveredAt = deliveredAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }
}
