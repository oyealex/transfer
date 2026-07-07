package com.ecommerce.cart.cache;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * POJO representing a complete cart stored in Caffeine Cache.
 */
public class CartData {

    private Long userId;
    private List<CartItemData> items;
    private LocalDateTime updatedAt;

    public CartData() {
        this.items = new ArrayList<>();
        this.updatedAt = LocalDateTime.now();
    }

    public CartData(Long userId) {
        this();
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<CartItemData> getItems() {
        return items;
    }

    public void setItems(List<CartItemData> items) {
        this.items = items;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
