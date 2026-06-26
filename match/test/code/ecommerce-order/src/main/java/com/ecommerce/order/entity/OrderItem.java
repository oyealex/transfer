package com.ecommerce.order.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.math.BigDecimal;

/**
 * A single line item within an order.
 */
@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order_id", columnList = "orderId"),
        @Index(name = "idx_order_items_sku_id", columnList = "skuId")
})
public class OrderItem extends BaseEntity {

    /** Parent order ID */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** SKU ID at time of purchase */
    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    /** SKU name snapshot at time of purchase */
    @Column(name = "sku_name", nullable = false, length = 256)
    private String skuName;

    /** SKU code snapshot at time of purchase */
    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    /** Unit price at time of purchase */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Quantity ordered */
    @Column(nullable = false)
    private int quantity;

    /** Line subtotal: price * quantity */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /** JSON snapshot of the full product data at order time */
    @Column(name = "product_snapshot", columnDefinition = "TEXT")
    private String productSnapshot;

    public OrderItem() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public String getProductSnapshot() {
        return productSnapshot;
    }

    public void setProductSnapshot(String productSnapshot) {
        this.productSnapshot = productSnapshot;
    }
}
