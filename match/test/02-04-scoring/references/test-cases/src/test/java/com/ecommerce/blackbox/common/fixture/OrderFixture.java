package com.ecommerce.blackbox.common.fixture;

import com.ecommerce.blackbox.common.ApiClient;
import com.ecommerce.blackbox.common.BlackboxTestBase;
import com.ecommerce.blackbox.common.TestRunContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST fixture for order operations.
 * <p>
 * All methods issue real HTTP calls against the running application and can
 * be composed with other fixtures to build Given-When-Then scenarios.
 */
public class OrderFixture extends BlackboxTestBase {

    public OrderFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Order operations
    // ----------------------------------------------------------------

    /**
     * Creates a new order.
     *
     * @param ctx              test-run context
     * @param userToken        authenticated user JWT
     * @param addressId        delivery address id
     * @param items            list of {@link OrderItemRequest}
     * @param couponIds        optional coupon ids to apply
     * @param redeemPoints     points to redeem (0 if none)
     * @param externalOrderNo  unique external order number
     * @return parsed {@link OrderResult}
     */
    public OrderResult createOrder(TestRunContext ctx, String userToken,
                                   String addressId,
                                   List<OrderItemRequest> items,
                                   List<String> couponIds,
                                   int redeemPoints,
                                   String externalOrderNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("items", items);
        body.put("couponIds", couponIds != null ? couponIds : List.of());
        body.put("redeemPoints", redeemPoints);
        body.put("externalOrderNo", externalOrderNo);

        ResponseEntity<String> response = apiClient.post("/api/v1/orders/create", body, userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseOrderResult(response.getBody());
        }
        return new OrderResult();
    }

    /**
     * Retrieves an order by its id.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @param orderId   order id
     * @return raw HTTP response
     */
    public ResponseEntity<String> getOrder(TestRunContext ctx, String userToken,
                                           String orderId) {
        return apiClient.get("/api/v1/orders/" + orderId, userHeaders(userToken));
    }

    public String findFirstOrderItemId(TestRunContext ctx, String userToken, String orderId) {
        ResponseEntity<String> response = getOrder(ctx, userToken, orderId);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }
        return parseFirstOrderItemId(response.getBody());
    }

    /**
     * Cancels an order.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @param orderId   order id to cancel
     * @return raw HTTP response
     */
    public ResponseEntity<String> cancelOrder(TestRunContext ctx, String userToken,
                                              String orderId) {
        return apiClient.post("/api/v1/orders/" + orderId + "/cancel", null, userHeaders(userToken));
    }

    /**
     * Batch creates multiple orders.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @param orders    list of {@link BatchOrderRequest}
     * @return raw HTTP response
     */
    public ResponseEntity<String> batchCreate(TestRunContext ctx, String userToken,
                                              List<BatchOrderRequest> orders) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orders", orders);
        body.put("continueOnError", true);
        return apiClient.post("/api/v1/orders/batch", body, userHeaders(userToken));
    }

    /**
     * Verifies whether a user has purchased a given product.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @param userId    user id
     * @param productId product (SPU/SKU) id
     * @return raw HTTP response
     */
    public ResponseEntity<String> verifyPurchase(TestRunContext ctx, String userToken,
                                                 String userId, String productId) {
        String query = "userId=" + userId + "&productId=" + productId;
        return apiClient.get("/api/v1/orders/verify-purchase?" + query,
                userHeaders(userToken));
    }

    /**
     * Reviews a cancellation request (admin).
     *
     * @param ctx        test-run context
     * @param adminToken admin JWT
     * @param orderId    order id under review
     * @param approved   whether to approve the cancellation
     * @return raw HTTP response
     */
    public ResponseEntity<String> cancelReview(TestRunContext ctx, String adminToken,
                                               String orderId, boolean approved) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("approved", approved);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return apiClient.post("/api/v1/admin/orders/" + orderId + "/cancel-review", body, headers);
    }

    /**
     * Retrieves sales statistics for a date range (admin).
     *
     * @param ctx        test-run context
     * @param adminToken admin JWT
     * @param startDate  start date string (e.g. "2025-01-01")
     * @param endDate    end date string (e.g. "2025-12-31")
     * @return raw HTTP response
     */
    public ResponseEntity<String> getSalesStatistics(TestRunContext ctx, String adminToken,
                                                     String startDate, String endDate) {
        String query = "startDate=" + startDate + "&endDate=" + endDate;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return apiClient.get("/api/v1/admin/orders/statistics/sales?" + query, headers);
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private OrderResult parseOrderResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);

            // The order data may be at the root or nested under "data"
            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            OrderResult result = new OrderResult();
            if (data.has("orderId")) {
                result.setOrderId(data.get("orderId").asText());
            }
            if (data.has("id") && result.getOrderId() == null) {
                result.setOrderId(data.get("id").asText());
            }
            if (data.has("orderNo")) {
                result.setOrderNo(data.get("orderNo").asText());
            }
            if (data.has("status")) {
                result.setStatus(data.get("status").asText());
            }
            if (data.has("itemTotal")) {
                result.setItemTotal(new BigDecimal(data.get("itemTotal").asText()));
            }
            if (data.has("shippingFee")) {
                result.setShippingFee(new BigDecimal(data.get("shippingFee").asText()));
            }
            if (data.has("packagingFee")) {
                result.setPackagingFee(new BigDecimal(data.get("packagingFee").asText()));
            }
            if (data.has("discountAmount")) {
                result.setDiscountAmount(new BigDecimal(data.get("discountAmount").asText()));
            }
            if (data.has("pointsDeductionAmount")) {
                result.setPointsDeductionAmount(new BigDecimal(data.get("pointsDeductionAmount").asText()));
            }
            if (data.has("payableAmount")) {
                result.setPayableAmount(new BigDecimal(data.get("payableAmount").asText()));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OrderResult from JSON: " + e.getMessage(), e);
        }
    }

    private String parseFirstOrderItemId(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode data = root;
            if (root.has("data") && root.get("data").isObject()) {
                data = root.get("data");
            }

            for (String arrayField : List.of("items", "orderItems", "orderItemList",
                    "details", "orderDetails")) {
                JsonNode items = data.get(arrayField);
                if (items != null && items.isArray() && !items.isEmpty()) {
                    JsonNode first = items.get(0);
                    for (String idField : List.of("orderItemId", "id", "itemId")) {
                        JsonNode id = first.get(idField);
                        if (id != null && !id.isNull()) {
                            return id.asText();
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse order item id from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Request / Result POJOs
    // ----------------------------------------------------------------

    /**
     * Parsed representation of a created order response.
     */
    public static class OrderResult {
        private String orderId;
        private String orderNo;
        private String status;
        private BigDecimal itemTotal = BigDecimal.ZERO;
        private BigDecimal shippingFee = BigDecimal.ZERO;
        private BigDecimal packagingFee = BigDecimal.ZERO;
        private BigDecimal discountAmount = BigDecimal.ZERO;
        private BigDecimal pointsDeductionAmount = BigDecimal.ZERO;
        private BigDecimal payableAmount = BigDecimal.ZERO;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public BigDecimal getItemTotal() { return itemTotal; }
        public void setItemTotal(BigDecimal itemTotal) { this.itemTotal = itemTotal; }

        public BigDecimal getShippingFee() { return shippingFee; }
        public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

        public BigDecimal getPackagingFee() { return packagingFee; }
        public void setPackagingFee(BigDecimal packagingFee) { this.packagingFee = packagingFee; }

        public BigDecimal getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

        public BigDecimal getPointsDeductionAmount() { return pointsDeductionAmount; }
        public void setPointsDeductionAmount(BigDecimal pointsDeductionAmount) { this.pointsDeductionAmount = pointsDeductionAmount; }

        public BigDecimal getPayableAmount() { return payableAmount; }
        public void setPayableAmount(BigDecimal payableAmount) { this.payableAmount = payableAmount; }
    }

    /**
     * An order line item in a creation request.
     */
    public static class OrderItemRequest {
        private String skuId;
        private int quantity;

        public OrderItemRequest() {}

        public OrderItemRequest(String skuId, int quantity) {
            this.skuId = skuId;
            this.quantity = quantity;
        }

        public String getSkuId() { return skuId; }
        public void setSkuId(String skuId) { this.skuId = skuId; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    /**
     * A single order within a batch-create request.
     */
    public static class BatchOrderRequest {
        private String addressId;
        private List<OrderItemRequest> items = new ArrayList<>();
        private List<String> couponIds = new ArrayList<>();
        private int redeemPoints;
        private String externalOrderNo;

        public BatchOrderRequest() {}

        public BatchOrderRequest(String addressId, List<OrderItemRequest> items,
                                 List<String> couponIds, int redeemPoints,
                                 String externalOrderNo) {
            this.addressId = addressId;
            this.items = items;
            this.couponIds = couponIds;
            this.redeemPoints = redeemPoints;
            this.externalOrderNo = externalOrderNo;
        }

        public String getAddressId() { return addressId; }
        public void setAddressId(String addressId) { this.addressId = addressId; }

        public List<OrderItemRequest> getItems() { return items; }
        public void setItems(List<OrderItemRequest> items) { this.items = items; }

        public List<String> getCouponIds() { return couponIds; }
        public void setCouponIds(List<String> couponIds) { this.couponIds = couponIds; }

        public int getRedeemPoints() { return redeemPoints; }
        public void setRedeemPoints(int redeemPoints) { this.redeemPoints = redeemPoints; }

        public String getExternalOrderNo() { return externalOrderNo; }
        public void setExternalOrderNo(String externalOrderNo) { this.externalOrderNo = externalOrderNo; }
    }
}
