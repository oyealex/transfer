package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Real-time metrics service for order operations.
 * Used by monitoring dashboards and operational alerts.
 */
@Service
@Transactional(readOnly = true)
public class OrderMetricsService {

    private static final Logger log = LoggerFactory.getLogger(OrderMetricsService.class);

    private final OrderRepository orderRepository;

    public OrderMetricsService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Get real-time order creation rate (orders per minute for the last 5 minutes).
     */
    public double getCreationRatePerMinute() {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        List<Order> recentOrders = orderRepository.findAll().stream()
                .filter(o -> o.getCreatedAt() != null
                        && o.getCreatedAt().isAfter(fiveMinutesAgo))
                .collect(Collectors.toList());
        return recentOrders.size() / 5.0;
    }

    /**
     * Get real-time payment rate (payments per minute for the last 5 minutes).
     */
    public double getPaymentRatePerMinute() {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        List<Order> recentPayments = orderRepository.findAll().stream()
                .filter(o -> o.getPaidAt() != null
                        && o.getPaidAt().isAfter(fiveMinutesAgo))
                .collect(Collectors.toList());
        return recentPayments.size() / 5.0;
    }

    /**
     * Get average order processing time (create to ship) for the last 24 hours.
     */
    public Duration getAverageProcessingTime() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        List<Duration> processingTimes = new ArrayList<>();

        for (Order order : orderRepository.findAll()) {
            if (order.getCreatedAt() != null
                    && order.getCreatedAt().isAfter(twentyFourHoursAgo)
                    && order.getStatus() == OrderStatus.SHIPPED
                    && order.getUpdatedAt() != null) {
                processingTimes.add(
                        Duration.between(order.getCreatedAt(), order.getUpdatedAt()));
            }
        }

        if (processingTimes.isEmpty()) {
            return Duration.ZERO;
        }

        long avgSeconds = (long) processingTimes.stream()
                .mapToLong(Duration::getSeconds)
                .average()
                .orElse(0);
        return Duration.ofSeconds(avgSeconds);
    }

    /**
     * Get status transition metrics (how long orders spend in each status).
     */
    public Map<String, Duration> getStatusDwellTime() {
        // Aggregate dwell times across all orders
        Map<String, List<Duration>> allDwellTimes = new LinkedHashMap<>();

        for (Order order : orderRepository.findAll()) {
            if (order.getCreatedAt() == null || order.getUpdatedAt() == null) continue;

            Duration totalTime = Duration.between(order.getCreatedAt(), order.getUpdatedAt());
            String lastStatus = order.getStatus().name();

            allDwellTimes.computeIfAbsent(lastStatus, k -> new ArrayList<>())
                    .add(totalTime);
        }

        Map<String, Duration> averages = new LinkedHashMap<>();
        for (Map.Entry<String, List<Duration>> entry : allDwellTimes.entrySet()) {
            long avgSeconds = (long) entry.getValue().stream()
                    .mapToLong(Duration::getSeconds)
                    .average()
                    .orElse(0);
            averages.put(entry.getKey(), Duration.ofSeconds(avgSeconds));
        }

        return averages;
    }

    /**
     * Get inventory efficiency metrics.
     * Tracks how much inventory is reserved but unpaid (potential waste).
     */
    public long getUnpaidReservedOrdersCount() {
        return orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.CREATED
                        || o.getStatus() == OrderStatus.PAYING)
                .count();
    }

    /**
     * Get order volume trend for the last 24 hours, grouped by hour.
     */
    public Map<Integer, Long> getHourlyOrderVolume() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        Map<Integer, Long> hourlyVolume = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            hourlyVolume.put(h, 0L);
        }

        for (Order order : orderRepository.findAll()) {
            if (order.getCreatedAt() != null
                    && order.getCreatedAt().isAfter(twentyFourHoursAgo)) {
                int hour = order.getCreatedAt().getHour();
                hourlyVolume.merge(hour, 1L, Long::sum);
            }
        }

        return hourlyVolume;
    }

    /**
     * Get system-wide order health metrics.
     */
    public OrderHealthMetrics getHealthMetrics() {
        OrderHealthMetrics metrics = new OrderHealthMetrics();

        List<Order> allOrders = orderRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        long totalActive = 0;
        long stuckPaying = 0;
        long stuckPaid = 0;
        long expired = 0;

        for (Order order : allOrders) {
            if (order.getStatus() == OrderStatus.CANCELLED
                    || order.getStatus() == OrderStatus.COMPLETED
                    || order.getStatus() == OrderStatus.CLOSED) {
                continue;
            }
            totalActive++;

            if (order.getStatus() == OrderStatus.PAYING
                    && order.getCreatedAt() != null
                    && order.getCreatedAt().isBefore(now.minusHours(1))) {
                stuckPaying++;
            }

            if (order.getStatus() == OrderStatus.PAID
                    && order.getPaidAt() != null
                    && order.getPaidAt().isBefore(now.minusHours(24))) {
                stuckPaid++;
            }

            if (order.getStatus() == OrderStatus.CREATED
                    && order.getExpiresAt() != null
                    && order.getExpiresAt().isBefore(now)) {
                expired++;
            }
        }

        metrics.setTotalActiveOrders(totalActive);
        metrics.setStuckPayingOrders(stuckPaying);
        metrics.setStuckPaidOrders(stuckPaid);
        metrics.setExpiredUnpaidOrders(expired);
        metrics.setHealthScore(calculateHealthScore(totalActive, stuckPaying, stuckPaid, expired));
        metrics.setCheckedAt(now);

        return metrics;
    }

    private int calculateHealthScore(long total, long stuckPaying, long stuckPaid, long expired) {
        if (total == 0) return 100;
        long issues = stuckPaying + stuckPaid + expired;
        double issueRatio = (double) issues / total;
        if (issueRatio <= 0.05) return 100;
        if (issueRatio <= 0.10) return 90;
        if (issueRatio <= 0.20) return 75;
        if (issueRatio <= 0.30) return 60;
        if (issueRatio <= 0.50) return 40;
        return 20;
    }

    /**
     * System health metrics.
     */
    public static class OrderHealthMetrics {
        private long totalActiveOrders;
        private long stuckPayingOrders;
        private long stuckPaidOrders;
        private long expiredUnpaidOrders;
        private int healthScore; // 0-100
        private LocalDateTime checkedAt;

        public long getTotalActiveOrders() { return totalActiveOrders; }
        public void setTotalActiveOrders(long n) { this.totalActiveOrders = n; }
        public long getStuckPayingOrders() { return stuckPayingOrders; }
        public void setStuckPayingOrders(long n) { this.stuckPayingOrders = n; }
        public long getStuckPaidOrders() { return stuckPaidOrders; }
        public void setStuckPaidOrders(long n) { this.stuckPaidOrders = n; }
        public long getExpiredUnpaidOrders() { return expiredUnpaidOrders; }
        public void setExpiredUnpaidOrders(long n) { this.expiredUnpaidOrders = n; }
        public int getHealthScore() { return healthScore; }
        public void setHealthScore(int s) { this.healthScore = s; }
        public LocalDateTime getCheckedAt() { return checkedAt; }
        public void setCheckedAt(LocalDateTime t) { this.checkedAt = t; }
    }
}
