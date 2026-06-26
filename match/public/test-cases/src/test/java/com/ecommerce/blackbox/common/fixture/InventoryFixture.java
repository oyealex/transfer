package com.ecommerce.blackbox.common.fixture;

import com.ecommerce.blackbox.common.ApiClient;
import com.ecommerce.blackbox.common.BlackboxTestBase;
import com.ecommerce.blackbox.common.TestRunContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST fixture for inventory operations: warehouse creation, inbound, stock
 * query, and availability check.
 * <p>
 * All methods issue real HTTP calls against the running application and can
 * be composed with other fixtures to build Given-When-Then scenarios.
 */
public class InventoryFixture extends BlackboxTestBase {

    public InventoryFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Inventory operations
    // ----------------------------------------------------------------

    /**
     * Creates a new warehouse. The warehouse name is derived from
     * {@code "WH-" + ctx.getTestRunId()} to match the GWT spec requirement
     * for unique warehouse codes per test run.
     *
     * @param ctx        per-test context
     * @param adminToken the admin JWT bearer token
     * @return the newly created warehouseId
     */
    public Long createWarehouse(TestRunContext ctx, String adminToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "WH-" + ctx.getTestRunId());
        body.put("province", "Guangdong");
        body.put("city", "Shenzhen");
        body.put("district", "Nanshan");
        body.put("detail", "No.1 Warehouse Rd");
        body.put("priority", 1);

        HttpHeaders headers = bearerJsonHeaders(adminToken);
        ResponseEntity<String> resp = apiClient.post("/api/v1/admin/warehouses", body, headers);
        return parseId(resp.getBody());
    }

    /**
     * Performs an inbound (stock-in) operation, adding quantity for a SKU in a
     * specific warehouse.
     *
     * @param ctx         per-test context
     * @param adminToken  the admin JWT bearer token
     * @param warehouseId target warehouse ID
     * @param skuId       target SKU ID
     * @param quantity    quantity to add (must be &ge; 1)
     */
    public void inbound(TestRunContext ctx, String adminToken,
                        Long warehouseId, Long skuId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseId", warehouseId);
        body.put("skuId", skuId);
        body.put("quantity", quantity);

        HttpHeaders headers = bearerJsonHeaders(adminToken);
        apiClient.post("/api/v1/admin/inventory/inbound", body, headers);
    }

    /**
     * Convenience method that creates a warehouse and performs an inbound in one
     * call.
     *
     * @param ctx        per-test context
     * @param adminToken the admin JWT bearer token
     * @param skuId      target SKU ID
     * @param quantity   quantity to inbound (must be &ge; 1)
     * @return parsed {@link WarehouseAndStockResult} with warehouseId, skuId, quantity
     */
    public WarehouseAndStockResult createWarehouseAndInbound(TestRunContext ctx, String adminToken,
                                                              Long skuId, int quantity) {
        Long warehouseId = createWarehouse(ctx, adminToken);
        inbound(ctx, adminToken, warehouseId, skuId, quantity);

        WarehouseAndStockResult result = new WarehouseAndStockResult();
        result.setWarehouseId(warehouseId);
        result.setSkuId(skuId);
        result.setQuantity(quantity);
        return result;
    }

    /**
     * Queries the stock summary for a given SKU (public endpoint, no auth
     * required).
     *
     * @param ctx   per-test context
     * @param skuId the SKU to query
     * @return parsed {@link StockResult} with onHandStock, reservedStock, and availableStock
     */
    public StockResult getStockSummary(TestRunContext ctx, Long skuId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = apiClient.get("/api/v1/inventory/sku/" + skuId, headers);
        return parseStockResult(resp.getBody());
    }

    /**
     * Checks whether a given quantity of a SKU is available (public endpoint).
     *
     * @param ctx      per-test context
     * @param skuId    the SKU to check
     * @param quantity the quantity to check availability for
     * @return raw HTTP response for assertion / extraction
     */
    public ResponseEntity<String> checkAvailability(TestRunContext ctx, Long skuId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skuId", skuId);
        body.put("quantity", quantity);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return apiClient.post("/api/v1/inventory/check", body, headers);
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private Long parseId(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);
            if (root.has("id")) {
                return root.get("id").asLong();
            }
            throw new RuntimeException("Response JSON does not contain 'id' field");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse id from JSON: " + e.getMessage(), e);
        }
    }

    private StockResult parseStockResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);
            StockResult result = new StockResult();
            if (root.has("skuId")) {
                result.setSkuId(root.get("skuId").asLong());
            }
            if (root.has("onHandStock")) {
                result.setOnHandStock(root.get("onHandStock").asInt());
            }
            if (root.has("reservedStock")) {
                result.setReservedStock(root.get("reservedStock").asInt());
            }
            if (root.has("availableStock")) {
                result.setAvailableStock(root.get("availableStock").asInt());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse StockResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Private header helpers
    // ----------------------------------------------------------------

    private static HttpHeaders bearerJsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return h;
    }

    // ----------------------------------------------------------------
    // Result POJOs
    // ----------------------------------------------------------------

    /**
     * Summary of stock for a given SKU.
     */
    public static class StockResult {
        private Long skuId;
        private int onHandStock;
        private int reservedStock;
        private int availableStock;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }

        public int getOnHandStock() { return onHandStock; }
        public void setOnHandStock(int onHandStock) { this.onHandStock = onHandStock; }

        public int getReservedStock() { return reservedStock; }
        public void setReservedStock(int reservedStock) { this.reservedStock = reservedStock; }

        public int getAvailableStock() { return availableStock; }
        public void setAvailableStock(int availableStock) { this.availableStock = availableStock; }
    }

    /**
     * Result of the createWarehouse + inbound chain.
     */
    public static class WarehouseAndStockResult {
        private Long warehouseId;
        private Long skuId;
        private int quantity;

        public Long getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
