package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for creating a new order.
 */
public class CreateOrderRequest {

    /** Address ID for shipping destination */
    @NotNull(message = "Address ID is required")
    private Long addressId;

    /** Order line items */
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    /** Coupon IDs to apply (optional) */
    private List<Long> couponIds;

    /** Number of loyalty points to redeem (optional) */
    private int redeemPoints;

    /** Optional external order number from client system */
    @Size(max = 128, message = "External order number must not exceed 128 characters")
    private String externalOrderNo;

    public CreateOrderRequest() {
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    public List<Long> getCouponIds() {
        return couponIds;
    }

    public void setCouponIds(List<Long> couponIds) {
        this.couponIds = couponIds;
    }

    public int getRedeemPoints() {
        return redeemPoints;
    }

    public void setRedeemPoints(int redeemPoints) {
        this.redeemPoints = redeemPoints;
    }

    public String getExternalOrderNo() {
        return externalOrderNo;
    }

    public void setExternalOrderNo(String externalOrderNo) {
        this.externalOrderNo = externalOrderNo;
    }

    /**
     * A single item in an order request.
     */
    public static class OrderItemRequest {

        @NotNull(message = "SKU ID is required")
        private Long skuId;

        @NotNull(message = "Quantity is required")
        private Integer quantity;

        public OrderItemRequest() {
        }

        public Long getSkuId() {
            return skuId;
        }

        public void setSkuId(Long skuId) {
            this.skuId = skuId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
