package com.ecommerce.product.dto;

import com.ecommerce.product.query.StockSummaryDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Full product detail response including SKU info, SPU info, brand, category, specs, and stock summary.
 */
public class ProductDetailResponse {

    private Long skuId;
    private Long spuId;
    private String name;
    private BigDecimal price;
    private String status;
    private StockSummaryDto stockSummary;
    private String spuName;
    private String brand;
    private String category;
    private Map<String, String> specs;
    private List<String> images;

    public ProductDetailResponse() {
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

    public StockSummaryDto getStockSummary() {
        return stockSummary;
    }

    public void setStockSummary(StockSummaryDto stockSummary) {
        this.stockSummary = stockSummary;
    }

    public String getSpuName() {
        return spuName;
    }

    public void setSpuName(String spuName) {
        this.spuName = spuName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Map<String, String> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<String, String> specs) {
        this.specs = specs;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }
}
