package com.ecommerce.product.query;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cross-module DTO that exposes SKU data to other services (inventory, order, cart, etc.)
 * without requiring them to depend on the JPA entity.
 */
public class SkuDto {

    private Long skuId;
    private Long spuId;
    private String skuCode;
    private String name;
    private BigDecimal price;
    private String status;
    private Map<String, String> specs;

    public SkuDto() {
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, String> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<String, String> specs) {
        this.specs = specs;
    }
}
