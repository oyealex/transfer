package com.ecommerce.inventory.query;

/**
 * DTO representing a summary of stock availability for a SKU.
 * Returned by {@link InventoryQueryService#getStockSummary(Long)}.
 */
public class StockSummaryDto {

    private int availableStock;
    private int reservedStock;

    public StockSummaryDto() {
    }

    public StockSummaryDto(int availableStock, int reservedStock) {
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(int availableStock) {
        this.availableStock = availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(int reservedStock) {
        this.reservedStock = reservedStock;
    }
}
