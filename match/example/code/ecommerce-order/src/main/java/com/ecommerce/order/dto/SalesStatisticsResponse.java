package com.ecommerce.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for sales statistics.
 */
public class SalesStatisticsResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private long totalOrders;
    private BigDecimal totalAmount;
    private BigDecimal totalDiscount;
    private List<DailyBreakdown> breakdown;

    public SalesStatisticsResponse() {
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getTotalDiscount() {
        return totalDiscount;
    }

    public void setTotalDiscount(BigDecimal totalDiscount) {
        this.totalDiscount = totalDiscount;
    }

    public List<DailyBreakdown> getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(List<DailyBreakdown> breakdown) {
        this.breakdown = breakdown;
    }

    /**
     * Per-day breakdown of sales statistics.
     */
    public static class DailyBreakdown {

        private LocalDate date;
        private long orderCount;
        private BigDecimal amount;
        private BigDecimal discount;

        public DailyBreakdown() {
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public long getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(long orderCount) {
            this.orderCount = orderCount;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getDiscount() {
            return discount;
        }

        public void setDiscount(BigDecimal discount) {
            this.discount = discount;
        }
    }
}
