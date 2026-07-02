package com.ecommerce.blackbox.common.fixture;

import com.ecommerce.blackbox.common.ApiClient;
import com.ecommerce.blackbox.common.BlackboxTestBase;
import com.ecommerce.blackbox.common.TestRunContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST fixture for payment operations.
 * <p>
 * All methods issue real HTTP calls against the running application and can
 * be composed with other fixtures to build Given-When-Then scenarios.
 */
public class PaymentFixture extends BlackboxTestBase {

    public static final String SIGNATURE_HEADER = "X-Payment-Signature";
    public static final String VALID_SIGNATURE = "valid-signature";

    public PaymentFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Payment operations
    // ----------------------------------------------------------------

    /**
     * Initiates a payment for an order.
     *
     * @param ctx             test-run context
     * @param userToken       authenticated user JWT
     * @param orderId         order to pay
     * @param amount          payment amount
     * @param clientPaymentNo unique client-side payment number
     * @return parsed {@link PaymentResult}
     */
    public PaymentResult pay(TestRunContext ctx, String userToken,
                             String orderId, BigDecimal amount,
                             String clientPaymentNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("amount", amount);
        body.put("method", "BALANCE");
        body.put("clientPaymentNo", clientPaymentNo);

        ResponseEntity<String> response = apiClient.post("/api/v1/payment/pay", body, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parsePaymentResult(response.getBody());
        }
        return new PaymentResult();
    }

    /**
     * Simulates a payment gateway callback.
     * <p>
     * This endpoint is called by the external payment provider and does not
     * carry a user Bearer token. The {@code X-Payment-Signature} header is used
     * instead for authenticity verification.
     *
     * @param ctx             test-run context
     * @param paymentNo       payment number returned by {@link #pay}
     * @param orderId         associated order id
     * @param amount          callback amount
     * @param signature       value for the {@code X-Payment-Signature} header
     * @return raw HTTP response
     */
    public ResponseEntity<String> callback(TestRunContext ctx,
                                           String paymentNo,
                                           String orderId,
                                           BigDecimal amount,
                                           String signature) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentNo", paymentNo);
        body.put("orderId", orderId);
        body.put("amount", amount);
        body.put("status", "SUCCESS");
        body.put("callbackSequence", callbackSequence(ctx, paymentNo));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(SIGNATURE_HEADER, signature);

        return apiClient.post("/api/v1/payment/callback", body, headers);
    }

    private String callbackSequence(TestRunContext ctx, String paymentNo) {
        return "CB-" + ctx.getTestRunId() + "-" + paymentNo;
    }

    /**
     * Convenience overload that uses the default valid signature.
     */
    public ResponseEntity<String> callback(TestRunContext ctx,
                                           String paymentNo,
                                           String orderId,
                                           BigDecimal amount) {
        return callback(ctx, paymentNo, orderId, amount, VALID_SIGNATURE);
    }

    /**
     * Retrieves a payment record by its number.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @param paymentNo payment number
     * @return raw HTTP response
     */
    public ResponseEntity<String> getPayment(TestRunContext ctx, String userToken,
                                             String paymentNo) {
        return apiClient.get("/api/v1/payment/" + paymentNo, userHeaders(userToken));
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private PaymentResult parsePaymentResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            // The payment data may be at the root or nested under "data"
            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            PaymentResult result = new PaymentResult();
            if (data.has("paymentNo")) {
                result.setPaymentNo(data.get("paymentNo").asText());
            }
            if (data.has("status")) {
                result.setStatus(data.get("status").asText());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse PaymentResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Result POJO
    // ----------------------------------------------------------------

    /**
     * Parsed representation of a POST /api/v1/payment/pay response.
     */
    public static class PaymentResult {
        private String paymentNo;
        private String status;

        public String getPaymentNo() { return paymentNo; }
        public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
