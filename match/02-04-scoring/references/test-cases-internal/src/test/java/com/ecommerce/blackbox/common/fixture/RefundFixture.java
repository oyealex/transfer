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
 * REST fixture for refund and settlement operations.
 * <p>
 * Covers refund application, admin review, warehouse acceptance,
 * refund status query, and settlement batch queries.
 */
public class RefundFixture extends BlackboxTestBase {

    public RefundFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Refund operations
    // ----------------------------------------------------------------

    /**
     * Applies for a refund for a paid order.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @param orderId   the order id
     * @param paymentNo the payment number
     * @param reason    refund reason text
     * @return parsed {@link RefundResult}
     */
    public RefundResult applyRefund(TestRunContext ctx, String userToken,
                                     String orderId, String paymentNo,
                                     String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("paymentNo", paymentNo);
        body.put("refundRequestNo", refundRequestNo(ctx, orderId, paymentNo));
        body.put("reason", reason);

        ResponseEntity<String> response = apiClient.post(
                "/api/v1/refunds/apply", body, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseRefundResult(response.getBody());
        }
        return new RefundResult();
    }

    private String refundRequestNo(TestRunContext ctx, String orderId, String paymentNo) {
        return "RR-" + ctx.getTestRunId() + "-" + orderId + "-" + paymentNo;
    }

    /**
     * Admin reviews a refund request (approve or reject).
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     * @param refundId   the refund id
     * @param approved   true to approve, false to reject
     */
    public void reviewRefund(TestRunContext ctx, String adminToken,
                              String refundId, boolean approved) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("approved", approved);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        apiClient.post("/api/v1/admin/refunds/" + refundId + "/review",
                body, headers);
    }

    /**
     * Warehouse confirms receipt (or rejection) of returned goods.
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     * @param refundId   the refund id
     * @param accepted   true if goods accepted, false otherwise
     */
    public void warehouseAccept(TestRunContext ctx, String adminToken,
                                 String refundId, boolean accepted) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accepted", accepted);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        apiClient.post("/api/v1/admin/refunds/" + refundId + "/warehouse-accept",
                body, headers);
    }

    /**
     * Queries the status of a specific refund.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @param refundId  the refund id
     * @return parsed {@link RefundResult}
     */
    public RefundResult getRefund(TestRunContext ctx, String userToken,
                                   String refundId) {
        ResponseEntity<String> response = apiClient.get(
                "/api/v1/refunds/" + refundId, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseRefundResult(response.getBody());
        }
        return new RefundResult();
    }

    /**
     * Queries settlement batches (admin endpoint, uses POST with optional filters).
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     * @return raw HTTP response
     */
    public ResponseEntity<String> getSettlementBatches(TestRunContext ctx,
                                                        String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return apiClient.post("/api/v1/admin/settlements/batches", null, headers);
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private RefundResult parseRefundResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            RefundResult result = new RefundResult();
            if (data.has("refundId")) {
                result.setRefundId(data.get("refundId").asText());
            }
            if (data.has("id")) {
                result.setRefundId(data.get("id").asText());
            }
            if (data.has("status")) {
                result.setStatus(data.get("status").asText());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse RefundResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Result POJO
    // ----------------------------------------------------------------

    /**
     * Parsed representation of refund API responses.
     */
    public static class RefundResult {
        private String refundId;
        private String status;

        public String getRefundId() { return refundId; }
        public void setRefundId(String refundId) { this.refundId = refundId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
