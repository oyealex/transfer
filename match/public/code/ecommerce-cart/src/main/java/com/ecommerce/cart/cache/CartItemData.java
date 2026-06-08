package com.ecommerce.cart.cache;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POJO representing a single cart item stored in Caffeine Cache.
 */
public class CartItemData {

    private Long skuId;
    private String skuName;
    private BigDecimal price;
    private Integer quantity;
    private LocalDateTime addedAt;

    public CartItemData() {
    }

    public CartItemData(Long skuId, String skuName, BigDecimal price, Integer quantity) {
        this.skuId = skuId;
        this.skuName = skuName;
        this.price = price;
        this.quantity = quantity;
        this.addedAt = LocalDateTime.now();
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getSkuName() {
        return skuName;
    }

    public void setSkuName(String skuName) {
        this.skuName = skuName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}
