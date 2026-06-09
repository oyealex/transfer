package com.ecommerce.logistics.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Tracks the movement and status changes of a shipment.
 *
 * <p>Each record represents a logistics event such as pickup,
 * transit arrival, delivery attempt, or exception.
 */
@Entity
@Table(name = "shipment_trackings", indexes = {
        @Index(name = "idx_shipment_trackings_shipment_id", columnList = "shipmentId"),
        @Index(name = "idx_shipment_trackings_event_time", columnList = "eventTime")
})
public class ShipmentTracking extends BaseEntity {

    /** Associated shipment ID */
    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    /** Status at this tracking point */
    @Column(nullable = false, length = 32)
    private String status;

    /** Geographic location of this event */
    @Column(length = 256)
    private String location;

    /** Human-readable description of the event */
    @Column(length = 512)
    private String description;

    /** When this event occurred */
    @Column(name = "event_time")
    private LocalDateTime eventTime;

    /** Operator or system that recorded this event */
    @Column(length = 64)
    private String operator;

    public ShipmentTracking() {
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
