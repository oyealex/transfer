package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for exporting order data in various formats.
 * Supports CSV, JSON, and summary report generation for admin and reconciliation purposes.
 */
@Service
@Transactional(readOnly = true)
public class OrderExportService {

    private static final Logger log = LoggerFactory.getLogger(OrderExportService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderExportService(OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * Export orders in a date range as CSV format.
     *
     * @param startDate start of date range
     * @param endDate   end of date range
     * @return CSV string
     */
    public String exportOrdersCsv(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("DATE_REQUIRED", "Start and end dates are required");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<Order> orders = filterByDateRange(orderRepository.findAll(), start, end);

        StringBuilder csv = new StringBuilder();
        // CSV header
        csv.append("OrderID,OrderNo,UserID,Status,ItemTotal,ShippingFee,PackagingFee,");
        csv.append("DiscountAmount,PointsDeduction,PayableAmount,PaidAmount,PaymentNo,");
        csv.append("RedeemedPoints,CouponIDs,CancelReason,CreatedAt,PaidAt,CancelledAt,ExpiresAt\n");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (Order order : orders) {
            csv.append(escapeCsv(order.getId()));
            csv.append(",").append(escapeCsv(order.getOrderNo()));
            csv.append(",").append(order.getUserId());
            csv.append(",").append(order.getStatus());
            csv.append(",").append(order.getItemTotal());
            csv.append(",").append(order.getShippingFee());
            csv.append(",").append(order.getPackagingFee());
            csv.append(",").append(order.getDiscountAmount());
            csv.append(",").append(order.getPointsDeductionAmount());
            csv.append(",").append(order.getPayableAmount());
            csv.append(",").append(order.getPaidAmount() != null ? order.getPaidAmount() : "");
            csv.append(",").append(escapeCsv(order.getPaymentNo()));
            csv.append(",").append(order.getRedeemedPoints());
            csv.append(",").append(escapeCsv(order.getCouponIds()));
            csv.append(",").append(escapeCsv(order.getCancelReason()));
            csv.append(",").append(order.getCreatedAt() != null ? order.getCreatedAt().format(dtf) : "");
            csv.append(",").append(order.getPaidAt() != null ? order.getPaidAt().format(dtf) : "");
            csv.append(",").append(order.getCancelledAt() != null ? order.getCancelledAt().format(dtf) : "");
            csv.append(",").append(order.getExpiresAt() != null ? order.getExpiresAt().format(dtf) : "");
            csv.append("\n");
        }

        log.info("Exported {} orders to CSV for date range {} to {}", orders.size(), startDate, endDate);
        return csv.toString();
    }

    /**
     * Export order items for a specific order as CSV.
     *
     * @param orderId the order ID
     * @return CSV string of order items
     */
    public String exportOrderItemsCsv(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        StringBuilder csv = new StringBuilder();
        csv.append("ItemID,OrderID,SkuID,SkuName,SkuCode,Price,Quantity,Subtotal\n");

        for (OrderItem item : items) {
            csv.append(item.getId());
            csv.append(",").append(item.getOrderId());
            csv.append(",").append(item.getSkuId());
            csv.append(",").append(escapeCsv(item.getSkuName()));
            csv.append(",").append(escapeCsv(item.getSkuCode()));
            csv.append(",").append(item.getPrice());
            csv.append(",").append(item.getQuantity());
            csv.append(",").append(item.getSubtotal());
            csv.append("\n");
        }

        log.info("Exported {} order items to CSV for orderId={}", items.size(), orderId);
        return csv.toString();
    }

    /**
     * Generate a summary report for a date range.
     *
     * @param startDate start of date range
     * @param endDate   end of date range
     * @return summary report as text
     */
    public String generateSummaryReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<Order> orders = filterByDateRange(orderRepository.findAll(), start, end);

        long totalCreated = 0, totalPaid = 0, totalCancelled = 0;
        long totalShipped = 0, totalDelivered = 0, totalCompleted = 0;
        long totalOther = 0;

        java.math.BigDecimal totalRevenue = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalDiscount = java.math.BigDecimal.ZERO;

        for (Order order : orders) {
            switch (order.getStatus()) {
                case CREATED: totalCreated++; break;
                case PAYING: totalCreated++; break;
                case PAID: totalPaid++; break;
                case PICKING: totalPaid++; break;
                case SHIPPED: totalShipped++; break;
                case DELIVERED: totalDelivered++; break;
                case COMPLETED: totalCompleted++; break;
                case CANCELLED: case CANCEL_REVIEWING: totalCancelled++; break;
                case REFUNDING: case REFUNDED: totalCancelled++; break;
                default: totalOther++;
            }
            if (order.getPayableAmount() != null
                    && (order.getStatus() == com.ecommerce.order.entity.OrderStatus.PAID
                    || order.getStatus() == com.ecommerce.order.entity.OrderStatus.PICKING
                    || order.getStatus() == com.ecommerce.order.entity.OrderStatus.SHIPPED
                    || order.getStatus() == com.ecommerce.order.entity.OrderStatus.DELIVERED
                    || order.getStatus() == com.ecommerce.order.entity.OrderStatus.COMPLETED)) {
                totalRevenue = totalRevenue.add(order.getPayableAmount());
            }
            if (order.getDiscountAmount() != null) {
                totalDiscount = totalDiscount.add(order.getDiscountAmount());
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("========================================\n");
        report.append("     ORDER SUMMARY REPORT\n");
        report.append("========================================\n");
        report.append("Period: ").append(startDate).append(" to ").append(endDate).append("\n");
        report.append("Generated: ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        report.append("----------------------------------------\n");
        report.append("Total Orders:        ").append(orders.size()).append("\n");
        report.append("  Created/Paying:    ").append(totalCreated).append("\n");
        report.append("  Paid/Processing:   ").append(totalPaid).append("\n");
        report.append("  Shipped:           ").append(totalShipped).append("\n");
        report.append("  Delivered:         ").append(totalDelivered).append("\n");
        report.append("  Completed:         ").append(totalCompleted).append("\n");
        report.append("  Cancelled:         ").append(totalCancelled).append("\n");
        report.append("  Other:             ").append(totalOther).append("\n");
        report.append("----------------------------------------\n");
        report.append("Total Revenue:       ").append(totalRevenue).append("\n");
        report.append("Total Discount:      ").append(totalDiscount).append("\n");
        report.append("Conversion Rate:     ");
        if (orders.size() > 0) {
            long paidOrders = totalPaid + totalShipped + totalDelivered + totalCompleted;
            double rate = (double) paidOrders / orders.size() * 100;
            report.append(String.format("%.1f%%", rate));
        } else {
            report.append("N/A");
        }
        report.append("\n========================================\n");

        log.info("Generated summary report for {} orders", orders.size());
        return report.toString();
    }

    /**
     * Export order data as a JSON array for API consumers.
     */
    public String exportOrdersJson(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<Order> orders = filterByDateRange(orderRepository.findAll(), start, end);

        StringBuilder json = new StringBuilder();
        json.append("[\n");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            json.append("  {\n");
            json.append("    \"orderId\": ").append(order.getId()).append(",\n");
            json.append("    \"orderNo\": \"").append(order.getOrderNo()).append("\",\n");
            json.append("    \"userId\": ").append(order.getUserId()).append(",\n");
            json.append("    \"status\": \"").append(order.getStatus()).append("\",\n");
            json.append("    \"itemTotal\": ").append(order.getItemTotal()).append(",\n");
            json.append("    \"shippingFee\": ").append(order.getShippingFee()).append(",\n");
            json.append("    \"packagingFee\": ").append(order.getPackagingFee()).append(",\n");
            json.append("    \"discountAmount\": ").append(order.getDiscountAmount()).append(",\n");
            json.append("    \"pointsDeductionAmount\": ").append(order.getPointsDeductionAmount()).append(",\n");
            json.append("    \"payableAmount\": ").append(order.getPayableAmount()).append(",\n");
            json.append("    \"paidAmount\": ");
            json.append(order.getPaidAmount() != null ? order.getPaidAmount().toString() : "null");
            json.append(",\n");
            json.append("    \"paymentNo\": ");
            json.append(order.getPaymentNo() != null ? "\"" + order.getPaymentNo() + "\"" : "null");
            json.append(",\n");
            json.append("    \"createdAt\": \"");
            json.append(order.getCreatedAt() != null ? order.getCreatedAt().format(dtf) : "");
            json.append("\",\n");
            json.append("    \"expiresAt\": \"");
            json.append(order.getExpiresAt() != null ? order.getExpiresAt().format(dtf) : "");
            json.append("\"\n");
            json.append("  }");
            if (i < orders.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("]\n");
        log.info("Exported {} orders to JSON", orders.size());
        return json.toString();
    }

    private List<Order> filterByDateRange(List<Order> allOrders, LocalDateTime start, LocalDateTime end) {
        return allOrders.stream()
                .filter(o -> o.getCreatedAt() != null
                        && !o.getCreatedAt().isBefore(start)
                        && o.getCreatedAt().isBefore(end))
                .collect(java.util.stream.Collectors.toList());
    }

    private String escapeCsv(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
