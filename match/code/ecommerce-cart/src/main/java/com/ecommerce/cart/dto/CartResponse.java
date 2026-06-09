package com.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for the full cart view.
 */
public class CartResponse {

    private List<CartItemResponse> items;
    private Integer totalItems;
    private BigDecimal totalAmount;

    public CartResponse() {
    }

    public CartResponse(List<CartItemResponse> items, Integer totalItems, BigDecimal totalAmount) {
        this.items = items;
        this.totalItems = totalItems;
        this.totalAmount = totalAmount;
    }

    public List<CartItemResponse> getItems() {
        return items;
    }

    public void setItems(List<CartItemResponse> items) {
        this.items = items;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
