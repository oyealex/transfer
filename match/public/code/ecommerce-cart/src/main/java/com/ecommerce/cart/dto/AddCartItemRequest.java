package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for adding an item to the cart.
 */
public class AddCartItemRequest {

    @NotNull(message = "skuId must not be null")
    private Long skuId;

    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    public AddCartItemRequest() {
    }

    public AddCartItemRequest(Long skuId, Integer quantity) {
        this.skuId = skuId;
        this.quantity = quantity;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
