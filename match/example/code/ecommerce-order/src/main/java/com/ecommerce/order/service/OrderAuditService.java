package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderEventLog;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderEventLogRepository;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for auditing orders and generating compliance/audit reports.
 * Provides order lifecycle analysis, anomaly detection, and audit trail generation.
 */
@Service
@Transactional(readOnly = true)
public class OrderAuditService {

    private static final Logger log = LoggerFactory.getLogger(OrderAuditService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventLogRepository eventLogRepository;
    private final OrderEventLogService eventLogService;

    public OrderAuditService(OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              OrderEventLogRepository eventLogRepository,
                              OrderEventLogService eventLogService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventLogRepository = eventLogRepository;
        this.eventLogService = eventLogService;
    }

    /**
     * Generate a complete audit report for an order.
     */
    public String generateAuditReport(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return "ORDER NOT FOUND: " + orderId;
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        String auditTrail = eventLogService.buildAuditTrail(orderId);
        java.util.Map<String, Long> durations = eventLogService.getStatusDurations(orderId);

        StringBuilder report = new StringBuilder();
        report.append("================================================\n");
        report.append("           ORDER AUDIT REPORT\n");
        report.append("================================================\n");
        report.append("Order ID:       ").append(order.getId()).append("\n");
        report.append("Order No:       ").append(order.getOrderNo()).append("\n");
        report.append("User ID:        ").append(order.getUserId()).append("\n");
        report.append("Current Status: ").append(order.getStatus()).append("\n");
        report.append("Created At:     ").append(order.getCreatedAt()).append("\n");
        report.append("Expires At:     ").append(order.getExpiresAt()).append("\n");
        report.append("Paid At:        ").append(
                order.getPaidAt() != null ? order.getPaidAt() : "N/A").append("\n");
        report.append("Payable Amount: ").append(order.getPayableAmount()).append("\n");
        report.append("Paid Amount:    ").append(
                order.getPaidAmount() != null ? order.getPaidAmount() : "N/A").append("\n");
        report.append("Payment No:     ").append(
                order.getPaymentNo() != null ? order.getPaymentNo() : "N/A").append("\n");
        report.append("Items Count:    ").append(items.size()).append("\n");
        report.append("------------------------------------------------\n");
        report.append("Items:\n");
        for (OrderItem item : items) {
            report.append("  - skuId=").append(item.getSkuId())
                    .append(", name=").append(item.getSkuName())
                    .append(", price=").append(item.getPrice())
                    .append(", qty=").append(item.getQuantity())
                    .append(", subtotal=").append(item.getSubtotal()).append("\n");
        }
        report.append("------------------------------------------------\n");
        report.append("Status Duration Summary:\n");
        for (Map.Entry<String, Long> entry : durations.entrySet()) {
            report.append("  ").append(entry.getKey()).append(": ")
                    .append(formatDuration(entry.getValue())).append("\n");
        }
        report.append("------------------------------------------------\n");
        report.append(auditTrail);
        return report.toString();
    }

    /**
     * Find anomalous orders that may have issues.
     * Checks for:
     * - Orders with PAID status but old paid timestamp (stuck)
     * - Orders that were paid after expiry
     * - Orders with negative or zero payable amounts
     * - Orders missing required fee fields
     */
    public List<AuditFinding> findAnomalies() {
        List<AuditFinding> findings = new ArrayList<>();
        List<Order> allOrders = orderRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (Order order : allOrders) {
            // Check for stuck PAID orders (paid more than 48 hours ago without progressing)
            if (order.getStatus() == OrderStatus.PAID && order.getPaidAt() != null) {
                if (order.getPaidAt().isBefore(now.minusHours(48))) {
                    findings.add(new AuditFinding(order.getId(), "STUCK_PAID",
                            "Order has been PAID for over 48 hours without progressing to PICKING"));
                }
            }

            // Check for orders paid after expiry
            if (order.getPaidAt() != null && order.getExpiresAt() != null
                    && order.getPaidAt().isAfter(order.getExpiresAt())) {
                findings.add(new AuditFinding(order.getId(), "PAID_AFTER_EXPIRY",
                        "Order was paid after expiry time"));
            }

            // Check for suspicious amounts
            if (order.getPayableAmount() != null
                    && order.getPayableAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                findings.add(new AuditFinding(order.getId(), "SUSPICIOUS_AMOUNT",
                        "Order payable amount is zero or negative: " + order.getPayableAmount()));
            }

            // Check for missing fee calculations
            if (order.getShippingFee() == null || order.getPackagingFee() == null) {
                findings.add(new AuditFinding(order.getId(), "MISSING_FEES",
                        "Order missing shipping or packaging fee calculation"));
            }

            // Check for cancelled without cancel reason
            if (order.getStatus() == OrderStatus.CANCELLED
                    && (order.getCancelReason() == null || order.getCancelReason().isEmpty())) {
                findings.add(new AuditFinding(order.getId(), "MISSING_CANCEL_REASON",
                        "Cancelled order has no cancel reason"));
            }
        }

        log.info("Audit found {} anomalies across {} orders", findings.size(), allOrders.size());
        findings.sort(Comparator.comparing(AuditFinding::getSeverity).reversed());
        return findings;
    }

    /**
     * Get statistics about order lifecycle anomalies.
     */
    public Map<String, Long> getAnomalySummary() {
        List<AuditFinding> findings = findAnomalies();
        return findings.stream()
                .collect(Collectors.groupingBy(AuditFinding::getType, Collectors.counting()));
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    /**
     * Represents a single audit finding.
     */
    public static class AuditFinding {
        private final Long orderId;
        private final String type;
        private final String description;
        private final int severity;

        public AuditFinding(Long orderId, String type, String description) {
            this.orderId = orderId;
            this.type = type;
            this.description = description;
            this.severity = calculateSeverity(type);
        }

        private int calculateSeverity(String type) {
            switch (type) {
                case "STUCK_PAID": return 4;
                case "PAID_AFTER_EXPIRY": return 5;
                case "SUSPICIOUS_AMOUNT": return 5;
                case "MISSING_FEES": return 3;
                case "MISSING_CANCEL_REASON": return 2;
                default: return 1;
            }
        }

        public Long getOrderId() { return orderId; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public int getSeverity() { return severity; }
    }
}
