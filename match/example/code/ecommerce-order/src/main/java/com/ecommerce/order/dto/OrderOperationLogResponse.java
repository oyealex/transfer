package com.ecommerce.order.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for admin operation logs on orders.
 */
public class OrderOperationLogResponse {

    private Long id;
    private Long orderId;
    private String operationType;
    private String description;
    private String targetStatus;
    private String operatorId;
    private LocalDateTime operatedAt;
    private String extraData;

    public OrderOperationLogResponse() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTargetStatus() { return targetStatus; }
    public void setTargetStatus(String targetStatus) { this.targetStatus = targetStatus; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public LocalDateTime getOperatedAt() { return operatedAt; }
    public void setOperatedAt(LocalDateTime operatedAt) { this.operatedAt = operatedAt; }

    public String getExtraData() { return extraData; }
    public void setExtraData(String extraData) { this.extraData = extraData; }
}
