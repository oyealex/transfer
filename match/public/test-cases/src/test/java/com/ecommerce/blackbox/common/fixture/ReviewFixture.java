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
 * REST fixture for product review operations.
 * <p>
 * Covers review creation, append, product-level query, user-level query,
 * and admin-side review approval / rejection.
 */
public class ReviewFixture extends BlackboxTestBase {

    public ReviewFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Review operations
    // ----------------------------------------------------------------

    /**
     * Creates a product review.
     *
     * @param ctx         test-run context
     * @param userToken   user Bearer token
     * @param productId   the product (SPU/SKU) id being reviewed
     * @param orderId     the order id associated with the review
     * @param orderItemId the order item id
     * @param rating      numeric rating (e.g. 1-5)
     * @param content     review text
     * @return parsed {@link ReviewResult}
     */
    public ReviewResult createReview(TestRunContext ctx, String userToken,
                                      String productId, String orderId,
                                      String orderItemId,
                                      int rating, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("orderId", orderId);
        if (orderItemId != null && !orderItemId.isBlank()) {
            body.put("orderItemId", orderItemId);
        }
        body.put("rating", rating);
        body.put("content", content);

        ResponseEntity<String> response = apiClient.post(
                "/api/v1/reviews", body, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseReviewResult(response.getBody());
        }
        return new ReviewResult();
    }

    /**
     * Appends additional content to an existing review.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @param reviewId  the review to append to
     * @param content   the additional text
     */
    public void appendReview(TestRunContext ctx, String userToken,
                              String reviewId, String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);

        apiClient.post("/api/v1/reviews/" + reviewId + "/append",
                body, userHeaders(userToken));
    }

    /**
     * Queries all reviews for a given product (public endpoint).
     *
     * @param ctx       test-run context
     * @param productId the product id
     * @return raw HTTP response
     */
    public ResponseEntity<String> getProductReviews(TestRunContext ctx, String productId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return apiClient.get("/api/v1/reviews/product/" + productId, headers);
    }

    /**
     * Queries the current user's own reviews.
     *
     * @param ctx       test-run context
     * @param userToken user Bearer token
     * @return raw HTTP response
     */
    public ResponseEntity<String> getMyReviews(TestRunContext ctx, String userToken) {
        return apiClient.get("/api/v1/reviews/my", userHeaders(userToken));
    }

    /**
     * Admin approves a review (makes it visible publicly).
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     * @param reviewId   the review id
     */
    public void approveReview(TestRunContext ctx, String adminToken, String reviewId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        apiClient.post("/api/v1/admin/reviews/" + reviewId + "/approve",
                null, headers);
    }

    /**
     * Admin rejects a review (hides or flags it).
     *
     * @param ctx        test-run context
     * @param adminToken admin Bearer token
     * @param reviewId   the review id
     */
    public void rejectReview(TestRunContext ctx, String adminToken, String reviewId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        apiClient.post("/api/v1/admin/reviews/" + reviewId + "/reject",
                null, headers);
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private ReviewResult parseReviewResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            ReviewResult result = new ReviewResult();
            if (data.has("id")) {
                result.setReviewId(String.valueOf(data.get("id").asLong()));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse ReviewResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Result POJO
    // ----------------------------------------------------------------

    /**
     * Parsed representation of POST /api/v1/reviews response.
     */
    public static class ReviewResult {
        private String reviewId;

        public String getReviewId() { return reviewId; }
        public void setReviewId(String reviewId) { this.reviewId = reviewId; }
    }
}
