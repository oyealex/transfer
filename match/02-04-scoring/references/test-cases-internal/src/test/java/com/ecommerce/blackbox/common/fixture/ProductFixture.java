package com.ecommerce.blackbox.common.fixture;

import com.ecommerce.blackbox.common.ApiClient;
import com.ecommerce.blackbox.common.BlackboxTestBase;
import com.ecommerce.blackbox.common.TestRunContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST fixture for product operations: SPU creation, SKU creation, shelf
 * management, product detail, and search.
 * <p>
 * All methods issue real HTTP calls against the running application and can
 * be composed with other fixtures to build Given-When-Then scenarios.
 */
public class ProductFixture extends BlackboxTestBase {

    private static final AtomicInteger spuCounter = new AtomicInteger(0);

    public ProductFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Product operations
    // ----------------------------------------------------------------

    /**
     * Creates a new SPU (Standard Product Unit).
     *
     * @param ctx        per-test context
     * @param adminToken the admin JWT bearer token
     * @return the newly created spuId
     */
    public Long createSpu(TestRunContext ctx, String adminToken) {
        int seq = spuCounter.incrementAndGet();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("spuCode", "SPU-" + ctx.getTestRunId() + "-" + seq);
        body.put("name", "Test Product SPU " + ctx.getTestRunId() + "-" + seq);
        body.put("description", "SPU created by blackbox fixture");
        body.put("categoryId", 1);

        HttpHeaders headers = bearerJsonHeaders(adminToken);
        ResponseEntity<String> resp = apiClient.post("/api/v1/admin/products/spu", body, headers);
        return parseId(resp.getBody());
    }

    /**
     * Creates a new SKU under an existing SPU.
     *
     * @param ctx        per-test context
     * @param adminToken the admin JWT bearer token
     * @param spuId      the parent SPU ID
     * @param skuCode    unique SKU code (use {@code ctx.uniqueSkuCode()})
     * @param price      unit sale price
     * @return the newly created skuId
     */
    public Long createSku(TestRunContext ctx, String adminToken,
                          Long spuId, String skuCode, BigDecimal price) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("spuId", spuId);
        body.put("skuCode", skuCode);
        body.put("name", "SKU " + skuCode);
        body.put("price", price);

        HttpHeaders headers = bearerJsonHeaders(adminToken);
        ResponseEntity<String> resp = apiClient.post("/api/v1/admin/products/sku", body, headers);
        return parseId(resp.getBody());
    }

    /**
     * Puts a SKU on shelf, making it available for sale.
     *
     * @param ctx        per-test context
     * @param adminToken the admin JWT bearer token
     * @param skuId      the SKU to put on shelf
     */
    public void onShelf(TestRunContext ctx, String adminToken, Long skuId) {
        HttpHeaders headers = bearerJsonHeaders(adminToken);
        apiClient.post("/api/v1/admin/products/sku/" + skuId + "/on-shelf", null, headers);
    }

    /**
     * Takes a SKU off shelf, making it unavailable for sale.
     *
     * @param ctx        per-test context
     * @param adminToken the admin JWT bearer token
     * @param skuId      the SKU to take off shelf
     */
    public void offShelf(TestRunContext ctx, String adminToken, Long skuId) {
        HttpHeaders headers = bearerJsonHeaders(adminToken);
        apiClient.post("/api/v1/admin/products/sku/" + skuId + "/off-shelf", null, headers);
    }

    /**
     * Convenience method that creates a SPU, a SKU, and puts it on shelf in one
     * call. The SKU code is derived from {@link TestRunContext#uniqueSkuCode()}.
     *
     * @param ctx        per-test context
     * @param adminToken the admin JWT bearer token
     * @param skuCode    unique SKU code (e.g. {@code ctx.uniqueSkuCode()})
     * @param price      unit sale price
     * @return parsed {@link SkuResult} containing spuId, skuId, and price
     */
    public SkuResult createOnShelfSku(TestRunContext ctx, String adminToken,
                                      String skuCode, BigDecimal price) {
        Long spuId = createSpu(ctx, adminToken);
        Long skuId = createSku(ctx, adminToken, spuId, skuCode, price);
        onShelf(ctx, adminToken, skuId);

        SkuResult result = new SkuResult();
        result.setSpuId(spuId);
        result.setSkuId(skuId);
        result.setPrice(price);
        return result;
    }

    /**
     * Queries product detail by SKU ID (public endpoint, no auth required).
     *
     * @param ctx   per-test context
     * @param skuId the SKU to look up
     * @return raw HTTP response for assertion / extraction
     */
    public ResponseEntity<String> getProductDetail(TestRunContext ctx, Long skuId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return apiClient.get("/api/v1/products/" + skuId, headers);
    }

    /**
     * Searches products by keyword (public endpoint, no auth required).
     *
     * @param ctx     per-test context
     * @param keyword the search keyword
     * @return raw HTTP response for assertion / extraction
     */
    public ResponseEntity<String> searchProducts(TestRunContext ctx, String keyword) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return apiClient.get("/api/v1/products/search?keyword=" + keyword, headers);
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
     * Result of the createSpu + createSku + onShelf chain.
     */
    public static class SkuResult {
        private Long spuId;
        private Long skuId;
        private BigDecimal price;

        public Long getSpuId() { return spuId; }
        public void setSpuId(Long spuId) { this.spuId = spuId; }

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }

    /**
     * Minimal product search result summary for fixture callers.
     */
    public static class SearchResult {
        private ResponseEntity<String> rawResponse;

        public SearchResult() {
        }

        public SearchResult(ResponseEntity<String> rawResponse) {
            this.rawResponse = rawResponse;
        }

        public ResponseEntity<String> getRawResponse() { return rawResponse; }
        public void setRawResponse(ResponseEntity<String> rawResponse) { this.rawResponse = rawResponse; }
    }
}
