package com.ecommerce.order.dto;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for order audit reports.
 */
public class OrderAuditReportResponse {

    private String auditReport;
    private List<AuditFindingDto> anomalies;
    private Map<String, Long> anomalySummary;

    public OrderAuditReportResponse() {
    }

    public String getAuditReport() { return auditReport; }
    public void setAuditReport(String auditReport) { this.auditReport = auditReport; }

    public List<AuditFindingDto> getAnomalies() { return anomalies; }
    public void setAnomalies(List<AuditFindingDto> anomalies) { this.anomalies = anomalies; }

    public Map<String, Long> getAnomalySummary() { return anomalySummary; }
    public void setAnomalySummary(Map<String, Long> anomalySummary) { this.anomalySummary = anomalySummary; }

    public static class AuditFindingDto {
        private Long orderId;
        private String type;
        private String description;
        private int severity;

        public AuditFindingDto() {
        }

        public AuditFindingDto(Long orderId, String type, String description, int severity) {
            this.orderId = orderId;
            this.type = type;
            this.description = description;
            this.severity = severity;
        }

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public int getSeverity() { return severity; }
        public void setSeverity(int severity) { this.severity = severity; }
    }
}
