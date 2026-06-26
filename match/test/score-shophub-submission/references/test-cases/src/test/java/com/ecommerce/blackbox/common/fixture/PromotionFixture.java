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
import java.util.List;
import java.util.Map;

/**
 * REST fixture for promotion / coupon operations.
 * <p>
 * All methods issue real HTTP calls against the running application and can
 * be composed with other fixtures to build Given-When-Then scenarios.
 */
public class PromotionFixture extends BlackboxTestBase {

    public PromotionFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Promotion operations
    // ----------------------------------------------------------------

    /**
     * Creates a coupon template via the admin promotions API.
     *
     * @param ctx              test-run context (for unique coupon code)
     * @param adminToken       admin Bearer token
     * @param name             coupon name
     * @param type             coupon type (e.g. "DISCOUNT")
     * @param discountValue    discount value (e.g. 0.8 for 20% off)
     * @param thresholdAmount  minimum order amount (may be null)
     * @param startTime        validity start ISO-8601 (may be null)
     * @param endTime          validity end ISO-8601 (may be null)
     * @return parsed {@link CouponResult}
     */
    public CouponResult createCoupon(TestRunContext ctx, String adminToken,
                                      String name, String type,
                                      double discountValue,
                                      BigDecimal thresholdAmount,
                                      String startTime, String endTime) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("code", ctx.uniqueCouponCode());
        body.put("type", type != null ? type : "DISCOUNT");
        body.put("discountValue", discountValue);
        if (thresholdAmount != null) {
            body.put("thresholdAmount", thresholdAmount);
        }
        if (startTime != null) {
            body.put("startTime", startTime);
        }
        if (endTime != null) {
            body.put("endTime", endTime);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = apiClient.post(
                "/api/v1/admin/promotions/coupons", body, headers);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseCouponResult(response.getBody());
        }
        return new CouponResult();
    }

    /**
     * Convenience overload without optional parameters.
     */
    public CouponResult createCoupon(TestRunContext ctx, String adminToken,
                                      String name, String type, double discountValue) {
        return createCoupon(ctx, adminToken, name, type, discountValue, null, null, null);
    }

    /**
     * Claims a coupon for the given user.
     *
     * @param ctx              test-run context
     * @param userToken        user Bearer token
     * @param couponTemplateId the template id from {@link #createCoupon}
     * @return parsed {@link ClaimResult}
     */
    public ClaimResult claimCoupon(TestRunContext ctx, String userToken,
                                    String couponTemplateId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("couponTemplateId", couponTemplateId);

        ResponseEntity<String> response = apiClient.post(
                "/api/v1/promotions/coupons/claim", body, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseClaimResult(response.getBody());
        }
        return new ClaimResult();
    }

    /**
     * Retrieves the current user's coupon wallet.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @return raw HTTP response (coupon list under $.data or root)
     */
    public ResponseEntity<String> getMyCoupons(TestRunContext ctx, String userToken) {
        return apiClient.get("/api/v1/promotions/coupons/my", userHeaders(userToken));
    }

    /**
     * Calculates promotion discounts for a set of items and coupons.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @param items     list of cart/order items (each a Map with keys like
     *                  skuId, quantity, price)
     * @param couponIds list of coupon ids to apply (may be null or empty)
     * @return parsed {@link PromotionCalculationResult}
     */
    public PromotionCalculationResult calculatePromotion(TestRunContext ctx,
                                                          String userToken,
                                                          List<Map<String, Object>> items,
                                                          List<String> couponIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        if (couponIds != null && !couponIds.isEmpty()) {
            body.put("couponIds", couponIds);
        }

        ResponseEntity<String> response = apiClient.post(
                "/api/v1/promotions/calculate", body, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parsePromotionCalculationResult(response.getBody());
        }
        return new PromotionCalculationResult();
    }

    /**
     * Creates a full-reduction promotion via the admin API.
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     * @param name       promotion name
     * @param threshold  order amount threshold
     * @param reduction  reduction amount when threshold is met
     * @return parsed {@link FullReductionResult}
     */
    public FullReductionResult createFullReduction(TestRunContext ctx,
                                                    String adminToken,
                                                    String name,
                                                    BigDecimal threshold,
                                                    BigDecimal reduction) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("threshold", threshold);
        body.put("reduction", reduction);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = apiClient.post(
                "/api/v1/admin/promotions/full-reductions", body, headers);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseFullReductionResult(response.getBody());
        }
        return new FullReductionResult();
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private CouponResult parseCouponResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            CouponResult result = new CouponResult();
            if (data.has("couponTemplateId")) {
                result.setCouponTemplateId(data.get("couponTemplateId").asText());
            }
            if (data.has("id")) {
                result.setCouponTemplateId(data.get("id").asText());
            }
            if (data.has("name")) {
                result.setName(data.get("name").asText());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CouponResult from JSON: " + e.getMessage(), e);
        }
    }

    private ClaimResult parseClaimResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            ClaimResult result = new ClaimResult();
            if (data.has("couponId")) {
                result.setCouponId(data.get("couponId").asText());
            }
            if (data.has("id")) {
                result.setCouponId(data.get("id").asText());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ClaimResult from JSON: " + e.getMessage(), e);
        }
    }

    private PromotionCalculationResult parsePromotionCalculationResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            PromotionCalculationResult result = new PromotionCalculationResult();
            if (data.has("discountAmount")) {
                result.setDiscountAmount(new BigDecimal(data.get("discountAmount").asText()));
            }
            if (data.has("finalAmount")) {
                result.setFinalAmount(new BigDecimal(data.get("finalAmount").asText()));
            }
            if (data.has("originalAmount")) {
                result.setOriginalAmount(new BigDecimal(data.get("originalAmount").asText()));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse PromotionCalculationResult from JSON: " + e.getMessage(), e);
        }
    }

    private FullReductionResult parseFullReductionResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            FullReductionResult result = new FullReductionResult();
            if (data.has("promotionId")) {
                result.setPromotionId(data.get("promotionId").asText());
            }
            if (data.has("id")) {
                result.setPromotionId(data.get("id").asText());
            }
            if (data.has("name")) {
                result.setName(data.get("name").asText());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse FullReductionResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Result POJOs
    // ----------------------------------------------------------------

    /**
     * Parsed representation of POST /api/v1/admin/promotions/coupons response.
     */
    public static class CouponResult {
        private String couponTemplateId;
        private String name;

        public String getCouponTemplateId() { return couponTemplateId; }
        public void setCouponTemplateId(String couponTemplateId) { this.couponTemplateId = couponTemplateId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * Parsed representation of POST /api/v1/promotions/coupons/claim response.
     */
    public static class ClaimResult {
        private String couponId;

        public String getCouponId() { return couponId; }
        public void setCouponId(String couponId) { this.couponId = couponId; }
    }

    /**
     * Parsed representation of POST /api/v1/promotions/calculate response.
     */
    public static class PromotionCalculationResult {
        private BigDecimal discountAmount = BigDecimal.ZERO;
        private BigDecimal finalAmount = BigDecimal.ZERO;
        private BigDecimal originalAmount = BigDecimal.ZERO;

        public BigDecimal getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

        public BigDecimal getFinalAmount() { return finalAmount; }
        public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }

        public BigDecimal getOriginalAmount() { return originalAmount; }
        public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    }

    /**
     * Parsed representation of POST /api/v1/admin/promotions/full-reductions response.
     */
    public static class FullReductionResult {
        private String promotionId;
        private String name;

        public String getPromotionId() { return promotionId; }
        public void setPromotionId(String promotionId) { this.promotionId = promotionId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
