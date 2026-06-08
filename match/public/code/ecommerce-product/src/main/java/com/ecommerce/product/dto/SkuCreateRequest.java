package com.ecommerce.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for creating a new SKU.
 */
public class SkuCreateRequest {

    @NotNull(message = "spuId is required")
    private Long spuId;

    @NotBlank(message = "skuCode is required")
    @Size(max = 64)
    private String skuCode;

    @NotBlank(message = "name is required")
    @Size(max = 200)
    private String name;

    @NotNull(message = "price is required")
    @PositiveOrZero(message = "price must be non-negative")
    private BigDecimal price;

    private BigDecimal marketPrice;

    private Map<String, String> specs;

    private String image;

    public SkuCreateRequest() {
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

    public BigDecimal getMarketPrice() {
        return marketPrice;
    }

    public void setMarketPrice(BigDecimal marketPrice) {
        this.marketPrice = marketPrice;
    }

    public Map<String, String> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<String, String> specs) {
        this.specs = specs;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
