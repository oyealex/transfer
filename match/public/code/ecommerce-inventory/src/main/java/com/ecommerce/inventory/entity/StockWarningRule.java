package com.ecommerce.inventory.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_warning_rule")
public class StockWarningRule extends BaseEntity {

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "warning_threshold")
    private int warningThreshold;

    @Column
    private boolean enabled;

    public StockWarningRule() {
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public int getWarningThreshold() {
        return warningThreshold;
    }

    public void setWarningThreshold(int warningThreshold) {
        this.warningThreshold = warningThreshold;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
