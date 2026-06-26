package com.ecommerce.order.dto;

/**
 * Response DTO for order cancellation.
 */
public class CancelOrderResponse {

    private Long orderId;
    private String status;
    private String message;

    public CancelOrderResponse() {
    }

    public CancelOrderResponse(Long orderId, String status, String message) {
        this.orderId = orderId;
        this.status = status;
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
