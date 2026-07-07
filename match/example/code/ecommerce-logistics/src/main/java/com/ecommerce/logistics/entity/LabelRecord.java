package com.ecommerce.logistics.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Record of a printed shipping label.
 *
 * <p>Each shipment may have one or more labels printed during its lifecycle.
 * The label contains carrier information and tracking number.
 */
@Entity
@Table(name = "label_records", indexes = {
        @Index(name = "idx_label_records_shipment_id", columnList = "shipmentId"),
        @Index(name = "idx_label_records_label_no", columnList = "labelNo")
})
public class LabelRecord extends BaseEntity {

    /** Associated shipment ID */
    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    /** Label number */
    @Column(name = "label_no", nullable = false, length = 64)
    private String labelNo;

    /** Carrier name */
    @Column(nullable = false, length = 64)
    private String carrier;

    /** Tracking number assigned to this label */
    @Column(name = "tracking_no", length = 128)
    private String trackingNo;

    /** When the label was printed */
    @Column(name = "printed_at")
    private LocalDateTime printedAt;

    public LabelRecord() {
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getLabelNo() {
        return labelNo;
    }

    public void setLabelNo(String labelNo) {
        this.labelNo = labelNo;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public LocalDateTime getPrintedAt() {
        return printedAt;
    }

    public void setPrintedAt(LocalDateTime printedAt) {
        this.printedAt = printedAt;
    }
}
