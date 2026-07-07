package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for batch admin operations on multiple orders.
 */
public class OrderBatchOperationRequest {

    @NotEmpty(message = "Order IDs list must not be empty")
    private List<Long> orderIds;

    @NotNull(message = "Operation type is required")
    private String operationType; // "MARK_AS_PICKING", "MARK_AS_SHIPPED", "CLOSE", etc.

    private String note;

    public OrderBatchOperationRequest() {
    }

    public List<Long> getOrderIds() { return orderIds; }
    public void setOrderIds(List<Long> orderIds) { this.orderIds = orderIds; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
