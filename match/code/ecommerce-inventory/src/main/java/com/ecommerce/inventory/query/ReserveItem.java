package com.ecommerce.inventory.query;

/**
 * Represents a single item to reserve in an order.
 * Used as input to {@link InventoryReservationService#reserve(Long, java.util.List)}.
 */
public class ReserveItem {

    private Long skuId;
    private int quantity;

    public ReserveItem() {
    }

    public ReserveItem(Long skuId, int quantity) {
        this.skuId = skuId;
        this.quantity = quantity;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
