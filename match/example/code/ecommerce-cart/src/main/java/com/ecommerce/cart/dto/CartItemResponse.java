package com.ecommerce.cart.dto;

import java.math.BigDecimal;

/**
 * Response DTO for a single cart item.
 */
public class CartItemResponse {

    private Long skuId;
    private String skuName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;

    public CartItemResponse() {
    }

    public CartItemResponse(Long skuId, String skuName, BigDecimal price, Integer quantity, BigDecimal subtotal) {
        this.skuId = skuId;
        this.skuName = skuName;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = subtotal;
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

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
