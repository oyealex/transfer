package com.ecommerce.order.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for order operational metrics.
 */
public class OrderMetricsResponse {

    private double creationRatePerMinute;
    private double paymentRatePerMinute;
    private long averageProcessingSeconds;
    private Map<String, Long> statusDwellSeconds;
    private long unpaidReservedOrders;
    private Map<Integer, Long> hourlyOrderVolume;
    private OrderHealthMetricsDto health;

    public OrderMetricsResponse() {
    }

    public double getCreationRatePerMinute() { return creationRatePerMinute; }
    public void setCreationRatePerMinute(double creationRatePerMinute) {
        this.creationRatePerMinute = creationRatePerMinute;
    }

    public double getPaymentRatePerMinute() { return paymentRatePerMinute; }
    public void setPaymentRatePerMinute(double paymentRatePerMinute) {
        this.paymentRatePerMinute = paymentRatePerMinute;
    }

    public long getAverageProcessingSeconds() { return averageProcessingSeconds; }
    public void setAverageProcessingSeconds(long averageProcessingSeconds) {
        this.averageProcessingSeconds = averageProcessingSeconds;
    }

    public Map<String, Long> getStatusDwellSeconds() { return statusDwellSeconds; }
    public void setStatusDwellSeconds(Map<String, Long> statusDwellSeconds) {
        this.statusDwellSeconds = statusDwellSeconds;
    }

    public long getUnpaidReservedOrders() { return unpaidReservedOrders; }
    public void setUnpaidReservedOrders(long unpaidReservedOrders) {
        this.unpaidReservedOrders = unpaidReservedOrders;
    }

    public Map<Integer, Long> getHourlyOrderVolume() { return hourlyOrderVolume; }
    public void setHourlyOrderVolume(Map<Integer, Long> hourlyOrderVolume) {
        this.hourlyOrderVolume = hourlyOrderVolume;
    }

    public OrderHealthMetricsDto getHealth() { return health; }
    public void setHealth(OrderHealthMetricsDto health) { this.health = health; }

    public static class OrderHealthMetricsDto {
        private long totalActiveOrders;
        private long stuckPayingOrders;
        private long stuckPaidOrders;
        private long expiredUnpaidOrders;
        private int healthScore;
        private LocalDateTime checkedAt;

        public long getTotalActiveOrders() { return totalActiveOrders; }
        public void setTotalActiveOrders(long totalActiveOrders) {
            this.totalActiveOrders = totalActiveOrders;
        }
        public long getStuckPayingOrders() { return stuckPayingOrders; }
        public void setStuckPayingOrders(long stuckPayingOrders) {
            this.stuckPayingOrders = stuckPayingOrders;
        }
        public long getStuckPaidOrders() { return stuckPaidOrders; }
        public void setStuckPaidOrders(long stuckPaidOrders) {
            this.stuckPaidOrders = stuckPaidOrders;
        }
        public long getExpiredUnpaidOrders() { return expiredUnpaidOrders; }
        public void setExpiredUnpaidOrders(long expiredUnpaidOrders) {
            this.expiredUnpaidOrders = expiredUnpaidOrders;
        }
        public int getHealthScore() { return healthScore; }
        public void setHealthScore(int healthScore) { this.healthScore = healthScore; }
        public LocalDateTime getCheckedAt() { return checkedAt; }
        public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
    }
}
