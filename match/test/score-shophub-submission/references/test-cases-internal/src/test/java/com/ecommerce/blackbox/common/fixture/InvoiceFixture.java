package com.ecommerce.blackbox.common.fixture;

import com.ecommerce.blackbox.common.ApiClient;
import com.ecommerce.blackbox.common.BlackboxTestBase;
import com.ecommerce.blackbox.common.TestRunContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST fixture for invoice operations.
 * <p>
 * Covers invoice creation and order-level invoice query.
 */
public class InvoiceFixture extends BlackboxTestBase {

    public InvoiceFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Invoice operations
    // ----------------------------------------------------------------

    /**
     * Creates an invoice for an order.
     *
     * @param ctx           test-run context
     * @param userToken     user Bearer token
     * @param orderId       the order id
     * @param invoiceType   invoice type (e.g. "PERSONAL", "COMPANY")
     * @param invoiceAmount the invoice amount
     * @param invoiceTitle  the invoice title / header text
     * @return parsed {@link InvoiceResult}
     */
    public InvoiceResult createInvoice(TestRunContext ctx, String userToken,
                                        String orderId, String invoiceType,
                                        BigDecimal invoiceAmount,
                                        String invoiceTitle) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("invoiceType", invoiceType);
        body.put("invoiceAmount", invoiceAmount);
        body.put("invoiceTitle", invoiceTitle);
        body.put("invoiceRequestNo",
                invoiceRequestNo(ctx, orderId, invoiceType, invoiceAmount, invoiceTitle));

        ResponseEntity<String> response = apiClient.post(
                "/api/v1/invoices", body, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseInvoiceResult(response.getBody());
        }
        return new InvoiceResult();
    }

    private String invoiceRequestNo(TestRunContext ctx, String orderId, String invoiceType,
                                    BigDecimal invoiceAmount, String invoiceTitle) {
        String amountKey = invoiceAmount.stripTrailingZeros().toPlainString().replace('.', '_');
        String titleKey = Integer.toUnsignedString(invoiceTitle.hashCode());
        return "IR-" + ctx.getTestRunId() + "-" + orderId + "-" + invoiceType + "-"
                + amountKey + "-" + titleKey;
    }

    /**
     * Retrieves all invoices associated with an order.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @param orderId   the order id
     * @return raw HTTP response
     */
    public ResponseEntity<String> getOrderInvoices(TestRunContext ctx, String userToken,
                                                    String orderId) {
        return apiClient.get("/api/v1/invoices/order/" + orderId, userHeaders(userToken));
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private InvoiceResult parseInvoiceResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            InvoiceResult result = new InvoiceResult();
            if (data.has("id")) {
                result.setInvoiceId(String.valueOf(data.get("id").asLong()));
            }
            if (data.has("invoiceAmount")) {
                result.setAmount(new BigDecimal(data.get("invoiceAmount").asText()));
            }
            if (data.has("taxAmount")) {
                result.setTaxAmount(new BigDecimal(data.get("taxAmount").asText()));
            }
            if (data.has("taxRate")) {
                result.setTaxRate(new BigDecimal(data.get("taxRate").asText()));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse InvoiceResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Result POJO
    // ----------------------------------------------------------------

    /**
     * Parsed representation of POST /api/v1/invoices response.
     */
    public static class InvoiceResult {
        private String invoiceId;
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal taxAmount = BigDecimal.ZERO;
        private BigDecimal taxRate = BigDecimal.ZERO;

        public String getInvoiceId() { return invoiceId; }
        public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public BigDecimal getTaxAmount() { return taxAmount; }
        public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

        public BigDecimal getTaxRate() { return taxRate; }
        public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    }
}
