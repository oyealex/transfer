package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for recording admin operation logs on orders.
 */
public class OrderAdminOperationLogRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotBlank(message = "Operation type is required")
    private String operationType;

    @NotBlank(message = "Operation description is required")
    private String description;

    private String targetStatus;
    private String extraData;

    public OrderAdminOperationLogRequest() {
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTargetStatus() { return targetStatus; }
    public void setTargetStatus(String targetStatus) { this.targetStatus = targetStatus; }

    public String getExtraData() { return extraData; }
    public void setExtraData(String extraData) { this.extraData = extraData; }
}
