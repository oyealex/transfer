package com.ecommerce.order.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.time.LocalDateTime;

/**
 * Audit log recording every status transition on an order.
 * Provides full traceability of the order lifecycle.
 */
@Entity
@Table(name = "order_event_logs", indexes = {
        @Index(name = "idx_event_logs_order_id", columnList = "orderId")
})
public class OrderEventLog extends BaseEntity {

    /** The order this event belongs to */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** Previous status before the transition */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private OrderStatus fromStatus;

    /** New status after the transition */
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private OrderStatus toStatus;

    /** Type of event that triggered the transition (e.g., "CREATE", "PAY", "CANCEL") */
    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    /** User or system actor who triggered the event (userId or "SYSTEM") */
    @Column(name = "operator_id", length = 64)
    private String operatorId;

    /** Human-readable note about the transition */
    @Column(length = 512)
    private String note;

    /** When the event occurred (may differ from JPA createdAt for auditing) */
    @Column(name = "created_at_log", nullable = false)
    private LocalDateTime createdAtLog;

    public OrderEventLog() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(OrderStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(OrderStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAtLog() {
        return createdAtLog;
    }

    public void setCreatedAtLog(LocalDateTime createdAtLog) {
        this.createdAtLog = createdAtLog;
    }
}
