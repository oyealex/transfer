package com.ecommerce.order.service;

import com.ecommerce.order.entity.OrderEventLog;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing order event logs.
 * Provides CRUD operations and querying capabilities for order audit trails.
 *
 * <p>Event logs record every status transition in an order's lifecycle,
 * providing a complete audit trail for compliance and debugging.
 */
@Service
@Transactional
public class OrderEventLogService {

    private static final Logger log = LoggerFactory.getLogger(OrderEventLogService.class);

    private final OrderEventLogRepository eventLogRepository;

    public OrderEventLogService(OrderEventLogRepository eventLogRepository) {
        this.eventLogRepository = eventLogRepository;
    }

    /**
     * Record a status transition event for an order.
     *
     * @param orderId    the order ID
     * @param fromStatus previous status (null for initial creation)
     * @param toStatus   new status
     * @param eventType  event type (e.g., "CREATE", "PAY", "CANCEL")
     * @param operatorId user or system identifier
     * @param note       human-readable note
     * @return the saved event log entry
     */
    public OrderEventLog recordEvent(Long orderId, OrderStatus fromStatus, OrderStatus toStatus,
                                      String eventType, String operatorId, String note) {
        OrderEventLog logEntry = new OrderEventLog();
        logEntry.setOrderId(orderId);
        logEntry.setFromStatus(fromStatus);
        logEntry.setToStatus(toStatus);
        logEntry.setEventType(eventType);
        logEntry.setOperatorId(operatorId);
        logEntry.setNote(note);
        logEntry.setCreatedAtLog(LocalDateTime.now());

        OrderEventLog saved = eventLogRepository.save(logEntry);
        log.debug("Recorded event for order {}: {} -> {}, type={}, operator={}",
                orderId, fromStatus, toStatus, eventType, operatorId);
        return saved;
    }

    /**
     * Get all event logs for an order, ordered by creation time ascending.
     *
     * @param orderId the order ID
     * @return list of event logs
     */
    @Transactional(readOnly = true)
    public List<OrderEventLog> getEventsByOrderId(Long orderId) {
        return eventLogRepository.findByOrderIdOrderByCreatedAtLogAsc(orderId);
    }

    /**
     * Get the most recent event for an order.
     *
     * @param orderId the order ID
     * @return the most recent event log, or null if none found
     */
    @Transactional(readOnly = true)
    public OrderEventLog getLatestEvent(Long orderId) {
        List<OrderEventLog> logs = eventLogRepository.findByOrderIdOrderByCreatedAtLogAsc(orderId);
        if (logs.isEmpty()) {
            return null;
        }
        return logs.get(logs.size() - 1);
    }

    /**
     * Count the total number of events for an order.
     *
     * @param orderId the order ID
     * @return event count
     */
    @Transactional(readOnly = true)
    public long countEvents(Long orderId) {
        return eventLogRepository.findByOrderIdOrderByCreatedAtLogAsc(orderId).size();
    }

    /**
     * Find events of a specific type for an order.
     *
     * @param orderId   the order ID
     * @param eventType the event type to filter by
     * @return list of matching event logs
     */
    @Transactional(readOnly = true)
    public List<OrderEventLog> findEventsByType(Long orderId, String eventType) {
        return eventLogRepository.findByOrderIdOrderByCreatedAtLogAsc(orderId).stream()
                .filter(e -> eventType.equals(e.getEventType()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Build an audit trail summary for an order.
     *
     * @param orderId the order ID
     * @return human-readable audit trail
     */
    @Transactional(readOnly = true)
    public String buildAuditTrail(Long orderId) {
        List<OrderEventLog> logs = getEventsByOrderId(orderId);
        if (logs.isEmpty()) {
            return "No events recorded for order " + orderId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== ORDER AUDIT TRAIL: ").append(orderId).append(" ===\n");

        for (OrderEventLog log : logs) {
            sb.append(String.format("[%s] %s", log.getCreatedAtLog(), log.getEventType()));
            sb.append(": ");
            if (log.getFromStatus() != null) {
                sb.append(log.getFromStatus()).append(" -> ");
            }
            sb.append(log.getToStatus());
            sb.append(" (by ").append(log.getOperatorId()).append(")");
            if (log.getNote() != null && !log.getNote().isEmpty()) {
                sb.append(" -- ").append(log.getNote());
            }
            sb.append("\n");
        }

        sb.append("=========================================\n");
        return sb.toString();
    }

    /**
     * Delete event logs older than the specified date.
     * Used for data retention compliance.
     *
     * @param before delete events older than this date
     * @return number of events deleted
     */
    @Transactional
    public int purgeOldEvents(LocalDateTime before) {
        List<OrderEventLog> allLogs = eventLogRepository.findAll();
        int deleted = 0;
        for (OrderEventLog log : allLogs) {
            if (log.getCreatedAtLog() != null && log.getCreatedAtLog().isBefore(before)) {
                eventLogRepository.delete(log);
                deleted++;
            }
        }
        log.info("Purged {} old order event logs (before {})", deleted, before);
        return deleted;
    }

    /**
     * Get the time spent in each status for an order.
     *
     * @param orderId the order ID
     * @return map of status names to durations in seconds
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getStatusDurations(Long orderId) {
        List<OrderEventLog> logs = getEventsByOrderId(orderId);
        java.util.Map<String, Long> durations = new java.util.LinkedHashMap<>();

        for (int i = 0; i < logs.size() - 1; i++) {
            OrderEventLog current = logs.get(i);
            OrderEventLog next = logs.get(i + 1);
            String statusName = current.getToStatus().name();
            long durationSeconds = java.time.Duration.between(
                    current.getCreatedAtLog(), next.getCreatedAtLog()).getSeconds();
            durations.merge(statusName, durationSeconds, Long::sum);
        }

        // Last status is still active
        if (!logs.isEmpty()) {
            OrderEventLog last = logs.get(logs.size() - 1);
            String statusName = last.getToStatus().name();
            long durationSeconds = java.time.Duration.between(
                    last.getCreatedAtLog(), LocalDateTime.now()).getSeconds();
            durations.merge(statusName, durationSeconds, Long::sum);
        }

        return durations;
    }
}
