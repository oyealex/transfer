package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for admin order status updates.
 */
public class AdminOrderUpdateRequest {

    @NotBlank(message = "Target status is required")
    private String targetStatus;

    private String note;

    public AdminOrderUpdateRequest() {
    }

    public String getTargetStatus() { return targetStatus; }
    public void setTargetStatus(String targetStatus) { this.targetStatus = targetStatus; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
