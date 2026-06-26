package com.ecommerce.order.analytics;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive order analytics service providing insights into order patterns,
 * customer behavior, revenue trends, and operational metrics.
 *
 * <p>Used by admin dashboards, reports, and business intelligence tools.
 */
@Service
@Transactional(readOnly = true)
public class OrderAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(OrderAnalyticsService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderAnalyticsService(OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * Calculate average order value over a date range.
     */
    public BigDecimal getAverageOrderValue(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        List<Order> paidOrders = orders.stream()
                .filter(o -> isPaidOrBetter(o.getStatus()))
                .collect(Collectors.toList());

        if (paidOrders.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = paidOrders.stream()
                .map(o -> o.getPayableAmount() != null ? o.getPayableAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(paidOrders.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Get revenue trend: daily revenue for the date range.
     */
    public Map<LocalDate, BigDecimal> getRevenueTrend(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        Map<LocalDate, BigDecimal> trend = new LinkedHashMap<>();

        // Initialize all dates with zero
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            trend.put(current, BigDecimal.ZERO);
            current = current.plusDays(1);
        }

        for (Order order : orders) {
            if (isPaidOrBetter(order.getStatus()) && order.getCreatedAt() != null) {
                LocalDate date = order.getCreatedAt().toLocalDate();
                if (trend.containsKey(date)) {
                    BigDecimal currentRevenue = trend.get(date);
                    BigDecimal orderAmount = order.getPayableAmount() != null
                            ? order.getPayableAmount() : BigDecimal.ZERO;
                    trend.put(date, currentRevenue.add(orderAmount));
                }
            }
        }

        return trend;
    }

    /**
     * Get order count by hour of day for the date range.
     */
    public Map<Integer, Long> getOrdersByHour(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        Map<Integer, Long> hourCounts = new LinkedHashMap<>();
        for (int i = 0; i < 24; i++) {
            hourCounts.put(i, 0L);
        }
        for (Order order : orders) {
            if (order.getCreatedAt() != null) {
                int hour = order.getCreatedAt().getHour();
                hourCounts.merge(hour, 1L, Long::sum);
            }
        }
        return hourCounts;
    }

    /**
     * Get order count by day of week.
     */
    public Map<DayOfWeek, Long> getOrdersByDayOfWeek(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        Map<DayOfWeek, Long> dayCounts = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            dayCounts.put(day, 0L);
        }
        for (Order order : orders) {
            if (order.getCreatedAt() != null) {
                DayOfWeek dow = order.getCreatedAt().getDayOfWeek();
                dayCounts.merge(dow, 1L, Long::sum);
            }
        }
        return dayCounts;
    }

    /**
     * Get status distribution for orders in the date range.
     */
    public Map<OrderStatus, Long> getStatusDistribution(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        Map<OrderStatus, Long> distribution = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            distribution.put(status, 0L);
        }
        for (Order order : orders) {
            distribution.merge(order.getStatus(), 1L, Long::sum);
        }
        return distribution;
    }

    /**
     * Get cancellation rate for the date range.
     */
    public double getCancellationRate(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        if (orders.isEmpty()) return 0.0;

        long cancelled = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED
                        || o.getStatus() == OrderStatus.CANCEL_REVIEWING
                        || o.getStatus() == OrderStatus.REFUNDING
                        || o.getStatus() == OrderStatus.REFUNDED)
                .count();

        return (double) cancelled / orders.size() * 100;
    }

    /**
     * Get payment conversion rate: percentage of created orders that get paid.
     */
    public double getConversionRate(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        if (orders.isEmpty()) return 0.0;

        long paid = orders.stream()
                .filter(o -> isPaidOrBetter(o.getStatus()))
                .count();

        return (double) paid / orders.size() * 100;
    }

    /**
     * Get top N customers by order count.
     */
    public List<CustomerRanking> getTopCustomers(int topN, LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        Map<Long, Long> customerOrderCounts = orders.stream()
                .collect(Collectors.groupingBy(Order::getUserId, Collectors.counting()));

        Map<Long, BigDecimal> customerRevenue = orders.stream()
                .filter(o -> isPaidOrBetter(o.getStatus()))
                .collect(Collectors.groupingBy(Order::getUserId,
                        Collectors.reducing(BigDecimal.ZERO,
                                o -> o.getPayableAmount() != null ? o.getPayableAmount() : BigDecimal.ZERO,
                                BigDecimal::add)));

        return customerOrderCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> new CustomerRanking(
                        entry.getKey(),
                        entry.getValue(),
                        customerRevenue.getOrDefault(entry.getKey(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    /**
     * Get discount effectiveness: total discounts given vs revenue.
     */
    public DiscountEffectiveness getDiscountEffectiveness(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        List<Order> paidOrders = orders.stream()
                .filter(o -> isPaidOrBetter(o.getStatus()))
                .collect(Collectors.toList());

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        long ordersWithDiscount = 0;

        for (Order order : paidOrders) {
            if (order.getPayableAmount() != null) {
                totalRevenue = totalRevenue.add(order.getPayableAmount());
            }
            if (order.getDiscountAmount() != null && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(order.getDiscountAmount());
                ordersWithDiscount++;
            }
        }

        DiscountEffectiveness de = new DiscountEffectiveness();
        de.setTotalRevenue(totalRevenue);
        de.setTotalDiscount(totalDiscount);
        de.setOrdersWithDiscount(ordersWithDiscount);
        de.setTotalPaidOrders(paidOrders.size());
        if (paidOrders.size() > 0 && totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            de.setDiscountRate(totalDiscount.divide(
                    totalRevenue.add(totalDiscount), 4, RoundingMode.HALF_UP));
            de.setDiscountUsageRate((double) ordersWithDiscount / paidOrders.size());
        }
        return de;
    }

    /**
     * Get time-to-payment metrics: how long users take to pay after creating an order.
     */
    public TimeToPaymentMetrics getTimeToPaymentMetrics(LocalDate startDate, LocalDate endDate) {
        List<Order> orders = getOrdersInRange(startDate, endDate);
        List<Long> minutesList = new ArrayList<>();

        for (Order order : orders) {
            if (isPaidOrBetter(order.getStatus())
                    && order.getCreatedAt() != null
                    && order.getPaidAt() != null) {
                long minutes = ChronoUnit.MINUTES.between(order.getCreatedAt(), order.getPaidAt());
                if (minutes >= 0) {
                    minutesList.add(minutes);
                }
            }
        }

        TimeToPaymentMetrics metrics = new TimeToPaymentMetrics();
        metrics.setTotalPaidOrders(minutesList.size());

        if (!minutesList.isEmpty()) {
            Collections.sort(minutesList);
            metrics.setMinMinutes(minutesList.get(0));
            metrics.setMaxMinutes(minutesList.get(minutesList.size() - 1));
            metrics.setAvgMinutes((long) minutesList.stream()
                    .mapToLong(Long::longValue).average().orElse(0));

            int medianIdx = minutesList.size() / 2;
            if (minutesList.size() % 2 == 0) {
                metrics.setMedianMinutes((minutesList.get(medianIdx - 1) + minutesList.get(medianIdx)) / 2);
            } else {
                metrics.setMedianMinutes(minutesList.get(medianIdx));
            }

            // Percentiles
            int p90Idx = (int) Math.ceil(0.9 * minutesList.size()) - 1;
            metrics.setP90Minutes(minutesList.get(Math.max(0, p90Idx)));

            // Buckets
            long within5min = minutesList.stream().filter(m -> m <= 5).count();
            long within30min = minutesList.stream().filter(m -> m <= 30).count();
            long within60min = minutesList.stream().filter(m -> m <= 60).count();

            metrics.setPaidWithin5min((double) within5min / minutesList.size());
            metrics.setPaidWithin30min((double) within30min / minutesList.size());
            metrics.setPaidWithin60min((double) within60min / minutesList.size());
        }

        return metrics;
    }

    // ======================== Private helpers ========================

    private List<Order> getOrdersInRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        return orderRepository.findAll().stream()
                .filter(o -> o.getCreatedAt() != null
                        && !o.getCreatedAt().isBefore(start)
                        && o.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());
    }

    private boolean isPaidOrBetter(OrderStatus status) {
        return status == OrderStatus.PAID
                || status == OrderStatus.PICKING
                || status == OrderStatus.SHIPPED
                || status == OrderStatus.DELIVERED
                || status == OrderStatus.COMPLETED;
    }

    // ======================== Analytics DTOs ========================

    public static class CustomerRanking {
        private final Long userId;
        private final long orderCount;
        private final BigDecimal totalRevenue;

        public CustomerRanking(Long userId, long orderCount, BigDecimal totalRevenue) {
            this.userId = userId;
            this.orderCount = orderCount;
            this.totalRevenue = totalRevenue;
        }
        public Long getUserId() { return userId; }
        public long getOrderCount() { return orderCount; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
    }

    public static class DiscountEffectiveness {
        private BigDecimal totalRevenue;
        private BigDecimal totalDiscount;
        private long ordersWithDiscount;
        private long totalPaidOrders;
        private BigDecimal discountRate;
        private double discountUsageRate;

        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
        public BigDecimal getTotalDiscount() { return totalDiscount; }
        public void setTotalDiscount(BigDecimal totalDiscount) { this.totalDiscount = totalDiscount; }
        public long getOrdersWithDiscount() { return ordersWithDiscount; }
        public void setOrdersWithDiscount(long ordersWithDiscount) { this.ordersWithDiscount = ordersWithDiscount; }
        public long getTotalPaidOrders() { return totalPaidOrders; }
        public void setTotalPaidOrders(long totalPaidOrders) { this.totalPaidOrders = totalPaidOrders; }
        public BigDecimal getDiscountRate() { return discountRate; }
        public void setDiscountRate(BigDecimal discountRate) { this.discountRate = discountRate; }
        public double getDiscountUsageRate() { return discountUsageRate; }
        public void setDiscountUsageRate(double discountUsageRate) { this.discountUsageRate = discountUsageRate; }
    }

    public static class TimeToPaymentMetrics {
        private long totalPaidOrders;
        private long minMinutes;
        private long maxMinutes;
        private long avgMinutes;
        private long medianMinutes;
        private long p90Minutes;
        private double paidWithin5min;
        private double paidWithin30min;
        private double paidWithin60min;

        public long getTotalPaidOrders() { return totalPaidOrders; }
        public void setTotalPaidOrders(long totalPaidOrders) { this.totalPaidOrders = totalPaidOrders; }
        public long getMinMinutes() { return minMinutes; }
        public void setMinMinutes(long minMinutes) { this.minMinutes = minMinutes; }
        public long getMaxMinutes() { return maxMinutes; }
        public void setMaxMinutes(long maxMinutes) { this.maxMinutes = maxMinutes; }
        public long getAvgMinutes() { return avgMinutes; }
        public void setAvgMinutes(long avgMinutes) { this.avgMinutes = avgMinutes; }
        public long getMedianMinutes() { return medianMinutes; }
        public void setMedianMinutes(long medianMinutes) { this.medianMinutes = medianMinutes; }
        public long getP90Minutes() { return p90Minutes; }
        public void setP90Minutes(long p90Minutes) { this.p90Minutes = p90Minutes; }
        public double getPaidWithin5min() { return paidWithin5min; }
        public void setPaidWithin5min(double paidWithin5min) { this.paidWithin5min = paidWithin5min; }
        public double getPaidWithin30min() { return paidWithin30min; }
        public void setPaidWithin30min(double paidWithin30min) { this.paidWithin30min = paidWithin30min; }
        public double getPaidWithin60min() { return paidWithin60min; }
        public void setPaidWithin60min(double paidWithin60min) { this.paidWithin60min = paidWithin60min; }
    }
}
