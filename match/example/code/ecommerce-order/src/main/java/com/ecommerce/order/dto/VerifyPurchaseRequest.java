package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for verifying whether a user purchased a product.
 */
public class VerifyPurchaseRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Product ID is required")
    private Long productId;

    public VerifyPurchaseRequest() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
}
