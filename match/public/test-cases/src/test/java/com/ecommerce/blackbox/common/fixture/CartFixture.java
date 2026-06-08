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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST fixture for cart operations.
 * <p>
 * All methods issue real HTTP calls against the running application and can
 * be composed with other fixtures to build Given-When-Then scenarios.
 */
public class CartFixture extends BlackboxTestBase {

    public CartFixture(ApiClient apiClient, TestRunContext testRunContext) {
        this.apiClient = apiClient;
        this.testRunContext = testRunContext;
    }

    // ----------------------------------------------------------------
    // Cart operations
    // ----------------------------------------------------------------

    /**
     * Adds an item to the cart.
     *
     * @param ctx       test-run context (unused here, kept for uniform fixture API)
     * @param userToken authenticated user JWT
     * @param skuId     SKU identifier
     * @param quantity  quantity to add
     * @return raw HTTP response
     */
    public ResponseEntity<String> addItem(TestRunContext ctx, String userToken,
                                          String skuId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skuId", skuId);
        body.put("quantity", quantity);
        return apiClient.post("/api/v1/cart/items", body, userHeaders(userToken));
    }

    /**
     * Retrieves the current user's cart.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @return parsed {@link CartResult}
     */
    public CartResult getCart(TestRunContext ctx, String userToken) {
        ResponseEntity<String> response = apiClient.get("/api/v1/cart", userHeaders(userToken));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseCartResult(response.getBody());
        }
        return new CartResult();
    }

    /**
     * Updates the quantity of a cart item.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @param skuId     SKU to update
     * @param quantity  new quantity value
     * @return raw HTTP response
     */
    public ResponseEntity<String> updateItem(TestRunContext ctx, String userToken,
                                             String skuId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quantity", quantity);
        return apiClient.put("/api/v1/cart/items/" + skuId, body, userHeaders(userToken));
    }

    /**
     * Removes an item from the cart.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @param skuId     SKU to remove
     * @return raw HTTP response
     */
    public ResponseEntity<String> removeItem(TestRunContext ctx, String userToken,
                                             String skuId) {
        return apiClient.delete("/api/v1/cart/items/" + skuId, userHeaders(userToken));
    }

    /**
     * Clears all items from the cart.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @return raw HTTP response
     */
    public ResponseEntity<String> clearCart(TestRunContext ctx, String userToken) {
        return apiClient.delete("/api/v1/cart", userHeaders(userToken));
    }

    /**
     * Estimates cart totals (item total, shipping, packaging, discounts, etc.)
     * without creating an order.
     *
     * @param ctx       test-run context
     * @param userToken authenticated user JWT
     * @return parsed {@link EstimateResult}
     */
    public EstimateResult estimate(TestRunContext ctx, String userToken) {
        HttpHeaders headers = userHeaders(userToken);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("couponIds", List.of());
        body.put("redeemPoints", 0);
        ResponseEntity<String> response = apiClient.post("/api/v1/cart/estimate", body, headers);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return parseEstimateResult(response.getBody());
        }
        return new EstimateResult();
    }

    // ----------------------------------------------------------------
    // JSON parsing helpers
    // ----------------------------------------------------------------

    private CartResult parseCartResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);
            CartResult result = new CartResult();
            if (root.has("cartId")) {
                result.setCartId(root.get("cartId").asText());
            }
            if (root.has("id") && !root.has("cartId")) {
                result.setCartId(root.get("id").asText());
            }
            if (root.has("items")) {
                List<CartItem> items = new ArrayList<>();
                for (JsonNode itemNode : root.get("items")) {
                    CartItem item = new CartItem();
                    if (itemNode.has("skuId")) {
                        item.setSkuId(itemNode.get("skuId").asText());
                    }
                    if (itemNode.has("skuName")) {
                        item.setSkuName(itemNode.get("skuName").asText());
                    }
                    if (itemNode.has("quantity")) {
                        item.setQuantity(itemNode.get("quantity").asInt());
                    }
                    if (itemNode.has("price")) {
                        item.setUnitPrice(new BigDecimal(itemNode.get("price").asText()));
                    }
                    if (itemNode.has("subtotal")) {
                        item.setSubtotal(new BigDecimal(itemNode.get("subtotal").asText()));
                    }
                    items.add(item);
                }
                result.setItems(items);
            }
            if (root.has("totalAmount")) {
                result.setTotalAmount(new BigDecimal(root.get("totalAmount").asText()));
            }
            if (root.has("totalItems")) {
                result.setTotalCount(root.get("totalItems").asInt());
            } else if (root.has("totalCount") || root.has("itemCount")) {
                JsonNode countNode = root.has("totalCount") ? root.get("totalCount") : root.get("itemCount");
                result.setTotalCount(countNode.asInt());
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CartResult from JSON: " + e.getMessage(), e);
        }
    }

    private EstimateResult parseEstimateResult(String json) {
        try {
            ObjectMapper mapper = apiClient.getObjectMapper();
            JsonNode root = mapper.readTree(json);
            EstimateResult result = new EstimateResult();
            if (root.has("itemTotal")) {
                result.setItemTotal(new BigDecimal(root.get("itemTotal").asText()));
            }
            if (root.has("shippingFee")) {
                result.setShippingFee(new BigDecimal(root.get("shippingFee").asText()));
            }
            if (root.has("packagingFee")) {
                result.setPackagingFee(new BigDecimal(root.get("packagingFee").asText()));
            }
            if (root.has("discountAmount")) {
                result.setDiscountAmount(new BigDecimal(root.get("discountAmount").asText()));
            }
            if (root.has("pointsDeductionAmount")) {
                result.setPointsDeductionAmount(new BigDecimal(root.get("pointsDeductionAmount").asText()));
            }
            if (root.has("payableAmount")) {
                result.setPayableAmount(new BigDecimal(root.get("payableAmount").asText()));
            }
            if (root.has("taxAmount")) {
                result.setTaxAmount(new BigDecimal(root.get("taxAmount").asText()));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse EstimateResult from JSON: " + e.getMessage(), e);
        }
    }

    // ----------------------------------------------------------------
    // Result POJOs
    // ----------------------------------------------------------------

    /**
     * Parsed representation of GET /api/v1/cart response.
     */
    public static class CartResult {
        private String cartId;
        private List<CartItem> items = Collections.emptyList();
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private int totalCount;

        public String getCartId() { return cartId; }
        public void setCartId(String cartId) { this.cartId = cartId; }

        public List<CartItem> getItems() { return items; }
        public void setItems(List<CartItem> items) { this.items = items; }

        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    }

    /**
     * A single cart line item.
     */
    public static class CartItem {
        private String skuId;
        private String skuName;
        private int quantity;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal subtotal = BigDecimal.ZERO;

        public String getSkuId() { return skuId; }
        public void setSkuId(String skuId) { this.skuId = skuId; }

        public String getSkuName() { return skuName; }
        public void setSkuName(String skuName) { this.skuName = skuName; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public BigDecimal getSubtotal() { return subtotal; }
        public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    }

    /**
     * Parsed representation of POST /api/v1/cart/estimate response.
     */
    public static class EstimateResult {
        private BigDecimal itemTotal = BigDecimal.ZERO;
        private BigDecimal shippingFee = BigDecimal.ZERO;
        private BigDecimal packagingFee = BigDecimal.ZERO;
        private BigDecimal discountAmount = BigDecimal.ZERO;
        private BigDecimal pointsDeductionAmount = BigDecimal.ZERO;
        private BigDecimal payableAmount = BigDecimal.ZERO;
        private BigDecimal taxAmount = BigDecimal.ZERO;

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

        public BigDecimal getTaxAmount() { return taxAmount; }
        public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    }
}
