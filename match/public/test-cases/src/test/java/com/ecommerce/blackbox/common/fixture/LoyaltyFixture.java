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
 * REST fixture for loyalty / points / member-level operations.
 * <p>
 * Covers point queries, redeem estimation, history, member level,
 * and admin-triggered point expiration.
 */
public class LoyaltyFixture extends BlackboxTestBase {

    public LoyaltyFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Loyalty operations
    // ----------------------------------------------------------------

    /**
     * Queries the current user's loyalty points.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @return parsed {@link PointsResult}
     */
    public PointsResult getPoints(TestRunContext ctx, String userToken) {
        ResponseEntity<String> response = apiClient.get(
                "/api/v1/loyalty/points", userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parsePointsResult(response.getBody());
        }
        return new PointsResult();
    }

    /**
     * Estimates the discount from redeeming points against an order amount.
     *
     * @param ctx          test-run context
     * @param userToken    user Bearer token
     * @param orderAmount  the target order amount
     * @param redeemPoints the number of points to redeem
     * @return parsed {@link RedeemEstimateResult}
     */
    public RedeemEstimateResult estimateRedeem(TestRunContext ctx, String userToken,
                                                BigDecimal orderAmount,
                                                int redeemPoints) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderAmount", orderAmount);
        body.put("redeemPoints", redeemPoints);

        ResponseEntity<String> response = apiClient.post(
                "/api/v1/loyalty/points/estimate-redeem", body, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseRedeemEstimateResult(response.getBody());
        }
        return new RedeemEstimateResult();
    }

    /**
     * Retrieves the current user's points acquisition/consumption history.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @return raw HTTP response
     */
    public ResponseEntity<String> getPointsHistory(TestRunContext ctx, String userToken) {
        return apiClient.get("/api/v1/loyalty/points/history", userHeaders(userToken));
    }

    /**
     * Queries the current user's member level.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @return parsed {@link MemberLevelResult}
     */
    public MemberLevelResult getMemberLevel(TestRunContext ctx, String userToken) {
        ResponseEntity<String> response = apiClient.get(
                "/api/v1/loyalty/member-level", userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseMemberLevelResult(response.getBody());
        }
        return new MemberLevelResult();
    }

    /**
     * Triggers admin-side batch expiration of stale points.
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     */
    public void expirePoints(TestRunContext ctx, String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        apiClient.post("/api/v1/admin/loyalty/points/expire", null, headers);
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private PointsResult parsePointsResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            PointsResult result = new PointsResult();
            if (data.has("totalPoints")) {
                result.setTotalPoints(data.get("totalPoints").asInt());
            }
            if (data.has("availablePoints")) {
                result.setAvailablePoints(data.get("availablePoints").asInt());
            }
            if (data.has("frozenPoints")) {
                result.setFrozenPoints(data.get("frozenPoints").asInt());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse PointsResult from JSON: " + e.getMessage(), e);
        }
    }

    private RedeemEstimateResult parseRedeemEstimateResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            RedeemEstimateResult result = new RedeemEstimateResult();
            if (data.has("redeemAmount")) {
                result.setRedeemAmount(new BigDecimal(data.get("redeemAmount").asText()));
            }
            if (data.has("deductedAmount")) {
                result.setDeductedAmount(new BigDecimal(data.get("deductedAmount").asText()));
            }
            if (data.has("redeemPoints")) {
                result.setRedeemPoints(data.get("redeemPoints").asInt());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse RedeemEstimateResult from JSON: " + e.getMessage(), e);
        }
    }

    private MemberLevelResult parseMemberLevelResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            MemberLevelResult result = new MemberLevelResult();
            if (data.has("level")) {
                result.setLevel(data.get("level").asText());
            }
            if (data.has("pointsToNextLevel")) {
                result.setPointsToNextLevel(data.get("pointsToNextLevel").asInt());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse MemberLevelResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Result POJOs
    // ----------------------------------------------------------------

    /**
     * Parsed representation of GET /api/v1/loyalty/points response.
     */
    public static class PointsResult {
        private int totalPoints;
        private int availablePoints;
        private int frozenPoints;

        public int getTotalPoints() { return totalPoints; }
        public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }

        public int getAvailablePoints() { return availablePoints; }
        public void setAvailablePoints(int availablePoints) { this.availablePoints = availablePoints; }

        public int getFrozenPoints() { return frozenPoints; }
        public void setFrozenPoints(int frozenPoints) { this.frozenPoints = frozenPoints; }
    }

    /**
     * Parsed representation of POST /api/v1/loyalty/points/estimate-redeem response.
     */
    public static class RedeemEstimateResult {
        private BigDecimal redeemAmount = BigDecimal.ZERO;
        private BigDecimal deductedAmount = BigDecimal.ZERO;
        private int redeemPoints;

        public BigDecimal getRedeemAmount() { return redeemAmount; }
        public void setRedeemAmount(BigDecimal redeemAmount) { this.redeemAmount = redeemAmount; }

        public BigDecimal getDeductedAmount() { return deductedAmount; }
        public void setDeductedAmount(BigDecimal deductedAmount) { this.deductedAmount = deductedAmount; }

        public int getRedeemPoints() { return redeemPoints; }
        public void setRedeemPoints(int redeemPoints) { this.redeemPoints = redeemPoints; }
    }

    /**
     * Parsed representation of GET /api/v1/loyalty/member-level response.
     */
    public static class MemberLevelResult {
        private String level;
        private int pointsToNextLevel;

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }

        public int getPointsToNextLevel() { return pointsToNextLevel; }
        public void setPointsToNextLevel(int pointsToNextLevel) { this.pointsToNextLevel = pointsToNextLevel; }
    }
}
