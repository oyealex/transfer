package com.ecommerce.promotion.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for calculating promotion discounts on an order.
 */
public class PromotionCalculateRequest {

    @NotEmpty
    private List<CalculateItem> items;

    private Long userId;

    private List<Long> couponIds;

    /**
     * A single item in the calculation request.
     */
    public static class CalculateItem {

        @NotNull
        private Long skuId;

        @NotNull
        private BigDecimal price;

        @NotNull
        private Integer quantity;

        public Long getSkuId() {
            return skuId;
        }

        public void setSkuId(Long skuId) {
            this.skuId = skuId;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

    // ---- getters and setters ----

    public List<CalculateItem> getItems() {
        return items;
    }

    public void setItems(List<CalculateItem> items) {
        this.items = items;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<Long> getCouponIds() {
        return couponIds;
    }

    public void setCouponIds(List<Long> couponIds) {
        this.couponIds = couponIds;
    }
}
