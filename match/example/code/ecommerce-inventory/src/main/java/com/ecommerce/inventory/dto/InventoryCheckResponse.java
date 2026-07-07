package com.ecommerce.inventory.dto;

public class InventoryCheckResponse {

    private Long skuId;
    private boolean available;
    private int availableStock;

    public InventoryCheckResponse() {
    }

    public InventoryCheckResponse(Long skuId, boolean available, int availableStock) {
        this.skuId = skuId;
        this.available = available;
        this.availableStock = availableStock;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(int availableStock) {
        this.availableStock = availableStock;
    }
}
