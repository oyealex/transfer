package com.ecommerce.order.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order status history with all transitions and timing.
 */
public class OrderStatusHistoryResponse {

    private Long orderId;
    private String orderNo;
    private String currentStatus;
    private List<StatusTransition> transitions;
    private String auditTrail; // Human-readable summary

    public OrderStatusHistoryResponse() {
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }

    public List<StatusTransition> getTransitions() { return transitions; }
    public void setTransitions(List<StatusTransition> transitions) { this.transitions = transitions; }

    public String getAuditTrail() { return auditTrail; }
    public void setAuditTrail(String auditTrail) { this.auditTrail = auditTrail; }

    public static class StatusTransition {
        private String fromStatus;
        private String toStatus;
        private String eventType;
        private String operatorId;
        private LocalDateTime occurredAt;
        private String note;
        private Long durationSeconds; // Time spent in fromStatus before this transition

        public StatusTransition() {
        }

        public String getFromStatus() { return fromStatus; }
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

        public String getToStatus() { return toStatus; }
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

        public LocalDateTime getOccurredAt() { return occurredAt; }
        public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }

        public Long getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }
    }
}
