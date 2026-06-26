package com.ecommerce.logistics.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Warehouse pick list for order fulfillment.
 *
 * <p>Each shipment can have one pick list that guides warehouse staff
 * in gathering the items for packing.
 */
@Entity
@Table(name = "pick_lists", indexes = {
        @Index(name = "idx_pick_lists_pick_list_no", columnList = "pickListNo", unique = true),
        @Index(name = "idx_pick_lists_shipment_id", columnList = "shipmentId"),
        @Index(name = "idx_pick_lists_warehouse_id", columnList = "warehouseId")
})
public class PickList extends BaseEntity {

    /** Unique pick list number */
    @Column(name = "pick_list_no", nullable = false, unique = true, length = 64)
    private String pickListNo;

    /** Associated shipment ID */
    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    /** Warehouse where picking occurs */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    /** JSON array of items to pick: [{"skuId":1,"skuName":"X","quantity":2,"location":"A-01-03"},...] */
    @Column(columnDefinition = "TEXT")
    private String items;

    /** Staff who performed the picking */
    @Column(name = "picker_id")
    private Long pickerId;

    /** Pick list status: PENDING, PICKING, COMPLETED */
    @Column(nullable = false, length = 32)
    private String status;

    public PickList() {
    }

    public String getPickListNo() {
        return pickListNo;
    }

    public void setPickListNo(String pickListNo) {
        this.pickListNo = pickListNo;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getItems() {
        return items;
    }

    public void setItems(String items) {
        this.items = items;
    }

    public Long getPickerId() {
        return pickerId;
    }

    public void setPickerId(Long pickerId) {
        this.pickerId = pickerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
