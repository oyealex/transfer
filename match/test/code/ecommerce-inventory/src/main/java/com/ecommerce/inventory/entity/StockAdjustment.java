package com.ecommerce.inventory.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_adjustment")
public class StockAdjustment extends BaseEntity {

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "before_qty")
    private int beforeQty;

    @Column(name = "after_qty")
    private int afterQty;

    @Column(length = 500)
    private String reason;

    public StockAdjustment() {
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public int getBeforeQty() {
        return beforeQty;
    }

    public void setBeforeQty(int beforeQty) {
        this.beforeQty = beforeQty;
    }

    public int getAfterQty() {
        return afterQty;
    }

    public void setAfterQty(int afterQty) {
        this.afterQty = afterQty;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
