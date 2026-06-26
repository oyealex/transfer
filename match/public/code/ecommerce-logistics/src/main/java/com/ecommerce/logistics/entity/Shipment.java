package com.ecommerce.logistics.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core shipment entity representing a delivery order.
 *
 * <p>A shipment is created after order payment and tracks the
 * physical delivery lifecycle from warehouse to customer.
 */
@Entity
@Table(name = "shipments", indexes = {
        @Index(name = "idx_shipments_shipment_no", columnList = "shipmentNo", unique = true),
        @Index(name = "idx_shipments_order_id", columnList = "orderId"),
        @Index(name = "idx_shipments_user_id", columnList = "userId"),
        @Index(name = "idx_shipments_status", columnList = "status")
})
public class Shipment extends BaseEntity {

    /** Unique shipment number */
    @Column(nullable = false, unique = true, length = 64)
    private String shipmentNo;

    /** Associated order ID */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** The user (buyer) this shipment belongs to */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Current shipment status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShipmentStatus status;

    /** Associated pick list ID */
    @Column(name = "pick_list_id")
    private Long pickListId;

    /** Shipping label number */
    @Column(name = "label_no", length = 64)
    private String labelNo;

    /** Carrier tracking number */
    @Column(name = "tracking_no", length = 128)
    private String trackingNo;

    /** Logistics carrier name */
    @Column(length = 64)
    private String carrier;

    /** Freight amount charged */
    @Column(name = "freight_amount", precision = 12, scale = 2)
    private BigDecimal freightAmount;

    /** When the package was picked up by carrier */
    @Column(name = "pickup_time")
    private LocalDateTime pickupTime;

    /** When the package was delivered */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** JSON snapshot of the delivery address */
    @Column(name = "address_snapshot", columnDefinition = "TEXT")
    private String addressSnapshot;

    public Shipment() {
    }

    public String getShipmentNo() {
        return shipmentNo;
    }

    public void setShipmentNo(String shipmentNo) {
        this.shipmentNo = shipmentNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public Long getPickListId() {
        return pickListId;
    }

    public void setPickListId(Long pickListId) {
        this.pickListId = pickListId;
    }

    public String getLabelNo() {
        return labelNo;
    }

    public void setLabelNo(String labelNo) {
        this.labelNo = labelNo;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public BigDecimal getFreightAmount() {
        return freightAmount;
    }

    public void setFreightAmount(BigDecimal freightAmount) {
        this.freightAmount = freightAmount;
    }

    public LocalDateTime getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(LocalDateTime pickupTime) {
        this.pickupTime = pickupTime;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getAddressSnapshot() {
        return addressSnapshot;
    }

    public void setAddressSnapshot(String addressSnapshot) {
        this.addressSnapshot = addressSnapshot;
    }
}
