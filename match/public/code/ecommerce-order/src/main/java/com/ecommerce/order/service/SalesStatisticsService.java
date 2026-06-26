package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.dto.SalesStatisticsResponse;
import com.ecommerce.order.dto.SalesStatisticsResponse.DailyBreakdown;
import com.ecommerce.order.dto.SalesStatisticsRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for aggregating sales statistics by date range.
 * Used by the admin dashboard for reporting.
 */
@Service
@Transactional(readOnly = true)
public class SalesStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(SalesStatisticsService.class);

    private final OrderRepository orderRepository;

    public SalesStatisticsService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Aggregate sales statistics for the given date range.
     *
     * @param request the date range request
     * @return aggregated sales statistics
     */
    public SalesStatisticsResponse getSalesStatistics(SalesStatisticsRequest request) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        if (startDate == null || endDate == null) {
            throw new BusinessException("INVALID_DATE_RANGE", "Start date and end date are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new BusinessException("INVALID_DATE_RANGE",
                    "Start date " + startDate + " is after end date " + endDate);
        }

        // Limit range to 90 days for performance
        if (startDate.until(endDate).getDays() > 90) {
            throw new BusinessException("DATE_RANGE_TOO_LARGE",
                    "Date range cannot exceed 90 days for performance reasons");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        log.info("Querying sales statistics from {} to {}", startDateTime, endDateTime);

        // Query orders in the date range (excluding CANCELLED and CLOSED)
        // In a real system, this would use a custom JPQL query.
        // Here we use a findAll approach with filtering.
        List<Order> allOrders = orderRepository.findAll();

        long totalOrders = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        // Group by date for daily breakdown
        Map<LocalDate, DailyBreakdown> dailyMap = new LinkedHashMap<>();

        for (Order order : allOrders) {
            // Only count paid-or-better orders (exclude CREATED, PAYING, CANCELLED, CLOSED)
            if (order.getStatus() == OrderStatus.CREATED
                    || order.getStatus() == OrderStatus.PAYING
                    || order.getStatus() == OrderStatus.CANCELLED
                    || order.getStatus() == OrderStatus.CLOSED) {
                continue;
            }

            LocalDateTime createdAt = order.getCreatedAt();
            if (createdAt == null) {
                continue;
            }

            // Check date range
            if (createdAt.isBefore(startDateTime) || createdAt.isAfter(endDateTime)) {
                continue;
            }

            totalOrders++;
            totalAmount = totalAmount.add(order.getPayableAmount() != null
                    ? order.getPayableAmount() : BigDecimal.ZERO);
            totalDiscount = totalDiscount.add(order.getDiscountAmount() != null
                    ? order.getDiscountAmount() : BigDecimal.ZERO);

            // Add to daily breakdown
            LocalDate date = createdAt.toLocalDate();
            DailyBreakdown daily = dailyMap.computeIfAbsent(date, k -> {
                DailyBreakdown db = new DailyBreakdown();
                db.setDate(k);
                db.setOrderCount(0);
                db.setAmount(BigDecimal.ZERO);
                db.setDiscount(BigDecimal.ZERO);
                return db;
            });
            daily.setOrderCount(daily.getOrderCount() + 1);
            daily.setAmount(daily.getAmount().add(order.getPayableAmount() != null
                    ? order.getPayableAmount() : BigDecimal.ZERO));
            daily.setDiscount(daily.getDiscount().add(order.getDiscountAmount() != null
                    ? order.getDiscountAmount() : BigDecimal.ZERO));
        }

        SalesStatisticsResponse response = new SalesStatisticsResponse();
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setTotalOrders(totalOrders);
        response.setTotalAmount(totalAmount);
        response.setTotalDiscount(totalDiscount);
        response.setBreakdown(new ArrayList<>(dailyMap.values()));

        log.info("Sales statistics: {} orders, total {} , discount {}",
                totalOrders, totalAmount, totalDiscount);

        return response;
    }
}
