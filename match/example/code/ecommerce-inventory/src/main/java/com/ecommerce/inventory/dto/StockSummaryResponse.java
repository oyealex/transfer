package com.ecommerce.inventory.dto;

public class StockSummaryResponse {

    private Long skuId;
    private String skuName;
    private int onHandStock;
    private int reservedStock;
    private int availableStock;

    public StockSummaryResponse() {
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

    public int getOnHandStock() {
        return onHandStock;
    }

    public void setOnHandStock(int onHandStock) {
        this.onHandStock = onHandStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(int reservedStock) {
        this.reservedStock = reservedStock;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(int availableStock) {
        this.availableStock = availableStock;
    }
}
