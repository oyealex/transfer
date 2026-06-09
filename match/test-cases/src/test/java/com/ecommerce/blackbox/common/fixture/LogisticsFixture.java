package com.ecommerce.blackbox.common.fixture;

import com.ecommerce.blackbox.common.ApiClient;
import com.ecommerce.blackbox.common.BlackboxTestBase;
import com.ecommerce.blackbox.common.TestRunContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST fixture for logistics / shipment operations.
 * <p>
 * Covers order-level logistics query, admin shipment lifecycle
 * (pick, print-label, outbound), and external logistics callback.
 */
public class LogisticsFixture extends BlackboxTestBase {

    public LogisticsFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Logistics operations
    // ----------------------------------------------------------------

    /**
     * Queries logistics details for an order.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @param orderId   the order id
     * @return parsed {@link LogisticsResult}
     */
    public LogisticsResult getLogistics(TestRunContext ctx, String userToken,
                                         String orderId) {
        ResponseEntity<String> response = apiClient.get(
                "/api/v1/logistics/order/" + orderId, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseLogisticsResult(response.getBody());
        }
        return new LogisticsResult();
    }

    /**
     * Marks a shipment as picked (warehouse staff has assembled the items).
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     * @param shipmentId the shipment id
     */
    public void pick(TestRunContext ctx, String adminToken, String shipmentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        apiClient.post("/api/v1/admin/logistics/shipments/" + shipmentId + "/pick",
                null, headers);
    }

    /**
     * Triggers label printing for a shipment.
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     * @param shipmentId the shipment id
     */
    public void printLabel(TestRunContext ctx, String adminToken, String shipmentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        apiClient.post("/api/v1/admin/logistics/shipments/" + shipmentId + "/print-label",
                null, headers);
    }

    /**
     * Marks a shipment as outbound (handed to carrier).
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     * @param shipmentId the shipment id
     */
    public void outbound(TestRunContext ctx, String adminToken, String shipmentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        apiClient.post("/api/v1/admin/logistics/shipments/" + shipmentId + "/outbound",
                null, headers);
    }

    /**
     * Simulates an external logistics carrier callback (no auth required).
     *
     * @param ctx        test-run context
     * @param trackingNo the carrier tracking number
     * @param status     the new status reported by the carrier (e.g. "DELIVERED")
     */
    public void logisticsCallback(TestRunContext ctx, String trackingNo, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trackingNo", trackingNo);
        body.put("status", status);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        apiClient.post("/api/v1/logistics/callback", body, headers);
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private LogisticsResult parseLogisticsResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            LogisticsResult result = new LogisticsResult();
            if (data.has("id")) {
                result.setShipmentId(String.valueOf(data.get("id").asLong()));
            } else if (data.has("shipmentId")) {
                result.setShipmentId(data.get("shipmentId").asText());
            }
            if (data.has("status")) {
                result.setStatus(data.get("status").asText());
            }
            if (data.has("trackingNo")) {
                result.setTrackingNo(data.get("trackingNo").asText());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse LogisticsResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Result POJO
    // ----------------------------------------------------------------

    /**
     * Parsed representation of GET /api/v1/logistics/order/{orderId} response.
     */
    public static class LogisticsResult {
        private String shipmentId;
        private String status;
        private String trackingNo;

        public String getShipmentId() { return shipmentId; }
        public void setShipmentId(String shipmentId) { this.shipmentId = shipmentId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    }
}
