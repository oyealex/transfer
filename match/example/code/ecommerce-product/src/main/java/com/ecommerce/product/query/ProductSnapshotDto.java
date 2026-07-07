package com.ecommerce.product.query;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A lightweight snapshot of product data used for order snapshots.
 * Captures the product state at the time an order is placed so historical
 * orders remain accurate even if product data changes later.
 */
public class ProductSnapshotDto {

    private Long skuId;
    private String name;
    private BigDecimal price;
    private String image;
    private Map<String, String> specs;

    public ProductSnapshotDto() {
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
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

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Map<String, String> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<String, String> specs) {
        this.specs = specs;
    }
}
