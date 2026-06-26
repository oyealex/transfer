package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for batch order creation.
 */
public class BatchCreateOrderRequest {

    /** List of individual order requests */
    @NotEmpty(message = "Batch must contain at least one order")
    @Valid
    private List<CreateOrderRequest> orders;

    /** If true, continue processing remaining orders even if some fail */
    private boolean continueOnError;

    public BatchCreateOrderRequest() {
    }

    public List<CreateOrderRequest> getOrders() {
        return orders;
    }

    public void setOrders(List<CreateOrderRequest> orders) {
        this.orders = orders;
    }

    public boolean isContinueOnError() {
        return continueOnError;
    }

    public void setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
    }
}
