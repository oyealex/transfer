package com.ecommerce.logistics.dto;

import java.time.LocalDateTime;

/**
 * Request DTO for logistics callback from carriers.
 *
 * <p>Carriers call this endpoint to report shipment status changes
 * such as pickup, in-transit, delivery, or exception events.
 */
public class LogisticsCallbackRequest {

    /** Carrier tracking number */
    private String trackingNo;

    /** New status: COLLECTED, IN_TRANSIT, DELIVERED, EXCEPTION */
    private String status;

    /** Geographic location of the event */
    private String location;

    /** Human-readable description */
    private String description;

    /** When the event occurred */
    private LocalDateTime eventTime;

    /** Signature or delivery confirmation code */
    private String signature;

    public LogisticsCallbackRequest() {
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
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

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
