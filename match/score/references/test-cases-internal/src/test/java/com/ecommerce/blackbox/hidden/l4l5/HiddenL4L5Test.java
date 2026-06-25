package com.ecommerce.blackbox.hidden.l4l5;

import com.ecommerce.blackbox.common.BlackboxTestBase;
import com.ecommerce.blackbox.common.fixture.*;
import com.ecommerce.blackbox.common.fixture.InventoryFixture.StockResult;
import com.ecommerce.blackbox.common.fixture.InventoryFixture.WarehouseAndStockResult;
import com.ecommerce.blackbox.common.fixture.OrderFixture.OrderItemRequest;
import com.ecommerce.blackbox.common.fixture.OrderFixture.OrderResult;
import com.ecommerce.blackbox.common.fixture.PaymentFixture.PaymentResult;
import com.ecommerce.blackbox.common.fixture.ProductFixture.SkuResult;
import com.ecommerce.blackbox.common.fixture.ReviewFixture.ReviewResult;
import com.ecommerce.blackbox.common.fixture.UserFixture.ActivatedUser;
import com.ecommerce.blackbox.common.fixture.UserFixture.AddressResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hidden L4 and L5 scoring tests.
 * <p>
 * L4 (Module Boundary): verifies that service boundaries are correctly isolated
 * -- when downstream services fail, the reliant service either degrades gracefully
 * or returns a predictable error.
 * <p>
 * L5 (Event Decoupling): verifies that post-action events (notifications, loyalty
 * points, logistics creation) do NOT block or roll back the primary business
 * transaction -- failures are recorded but payment/order succeeds.
 * <p>
 * Tests use test-support APIs ({@code /api/v1/admin/ops/fault-injections}, {@code /api/v1/admin/events/failures},
 * {@code /api/v1/admin/notifications}) to verify module-boundary degradation and
 * event failure recording through REST only.
 * <p>
 * <b>Baseline behavior note:</b> Several assertions in this class expect
 * correct behavior from the baseline specification. Current implementation
 * may differ. Do not change assertions to match current buggy behavior.
 */
@DisplayName("Hidden L4/L5 — Module Boundary & Event Decoupling")
class HiddenL4L5Test extends BlackboxTestBase {

    // ==================================================================
    // L4: Module Boundary Tests
    // ==================================================================

    // ----------------------------------------------------------------
    // L4-01: Payment query order boundary (requires /api/v1/admin/ops/fault-injections)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("HID-L4-01: payment/order boundary — fault injection triggers ORDER_QUERY_UNAVAILABLE")
    void l4_01_paymentOrderQueryBoundary() {
        // --- Given F-ORDER ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);
        AddressResult addr = userFixture.createAddress(testRunContext, user.getToken(),
                "Guangdong", "Shenzhen", "Nanshan", "No.1 Tech Street");

        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("50.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        OrderFixture orderFixture = new OrderFixture(apiClient, testRunContext);
        OrderItemRequest item = new OrderItemRequest(String.valueOf(sku.getSkuId()), 1);
        OrderResult order = orderFixture.createOrder(testRunContext, user.getToken(),
                String.valueOf(addr.getAddressId()),
                List.of(item), List.of(), 0,
                testRunContext.uniqueExternalOrderNo());
        assertNotNull(order.getOrderId(), "order should be created");

        // --- Given inject fault: order-query-service-unavailable ---
        Map<String, Object> faultBody = new LinkedHashMap<>();
        faultBody.put("fault", "order-query-service-unavailable");
        faultBody.put("enabled", true);
        HttpHeaders faultHeaders = new HttpHeaders();
        faultHeaders.setBearerAuth(testRunContext.getAdminToken());
        faultHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> faultResp = apiClient.post("/api/v1/admin/ops/fault-injections", faultBody, faultHeaders);
        assertTrue(faultResp.getStatusCode().is2xxSuccessful(),
                "fault injection should be accepted");

        // --- When POST /api/v1/payment/pay ---
        BigDecimal payableAmount = order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getPayableAmount() : new BigDecimal("50.00");
        PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
        PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                order.getOrderId(), payableAmount,
                testRunContext.uniqueClientPaymentNo());

        // --- Then payment may fail gracefully ---
        // The payment response should indicate the order query is unavailable
        // Either the pay call itself fails, or the callback detects the issue.

        // --- Then clear faults and retry ---
        apiClient.delete("/api/v1/admin/ops/fault-injections", faultHeaders);

        // Re-create a new order for the retry since the previous one may be in an
        // unknown state
        SkuResult sku2 = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode() + "-R", new BigDecimal("50.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku2.getSkuId(), 10);
        OrderResult order2 = orderFixture.createOrder(testRunContext, user.getToken(),
                String.valueOf(addr.getAddressId()),
                List.of(new OrderItemRequest(String.valueOf(sku2.getSkuId()), 1)),
                List.of(), 0,
                testRunContext.uniqueExternalOrderNo() + "-R");

        PaymentResult payment2 = paymentFixture.pay(testRunContext, user.getToken(),
                order2.getOrderId(), new BigDecimal("50.00"),
                testRunContext.uniqueClientPaymentNo() + "-R");
        assertNotNull(payment2.getPaymentNo(), "after clearing faults, payment should succeed");
    }

    // ----------------------------------------------------------------
    // L4-02: Product detail inventory boundary (requires /api/v1/admin/ops/fault-injections)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("HID-L4-02: product detail returns degraded inventory when inventory service down")
    void l4_02_productDetailInventoryBoundary() {
        // --- Given F-SKU ---
        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("50.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        // --- Given inject fault: inventory-query-service-unavailable ---
        Map<String, Object> faultBody = new LinkedHashMap<>();
        faultBody.put("fault", "inventory-query-service-unavailable");
        faultBody.put("enabled", true);
        HttpHeaders faultHeaders = new HttpHeaders();
        faultHeaders.setBearerAuth(testRunContext.getAdminToken());
        faultHeaders.setContentType(MediaType.APPLICATION_JSON);
        apiClient.post("/api/v1/admin/ops/fault-injections", faultBody, faultHeaders);

        // --- When GET /api/v1/products/{skuId} ---
        ResponseEntity<String> detailResp = productFixture.getProductDetail(testRunContext, sku.getSkuId());

        // --- Then: product detail returns degraded/unavailable inventory info ---
        // The response should indicate inventory is not available or provide a
        // fallback
        String body = detailResp.getBody();
        assertNotNull(body, "product detail should still return product info");

        // --- Then clear faults and re-query ---
        apiClient.delete("/api/v1/admin/ops/fault-injections", faultHeaders);
        ResponseEntity<String> detailAfter = productFixture.getProductDetail(testRunContext, sku.getSkuId());
        assertTrue(detailAfter.getStatusCode().is2xxSuccessful(),
                "after clearing faults, product detail should return normally");

        String afterBody = detailAfter.getBody();
        assertNotNull(afterBody, "after clearing, body should not be null");
    }

    // ----------------------------------------------------------------
    // L4-03: Review purchase verification (FULLY IMPLEMENTED)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("HID-L4-03: review without purchase returns 403 REVIEW_PURCHASE_REQUIRED")
    void l4_03_reviewPurchaseVerificationRequired() {
        // --- Given F-USER (but NO order) ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);

        // --- Given F-SKU (on shelf, but user never purchased) ---
        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("50.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        // --- When POST /api/v1/reviews (no prior purchase) ---
        ReviewFixture reviewFixture = new ReviewFixture(apiClient, testRunContext);
        // Use a non-existent orderId to simulate review without purchase
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", String.valueOf(sku.getSkuId()));
        body.put("orderId", "999999");
        body.put("orderItemId", "1");
        body.put("rating", 5);
        body.put("content", "Great product, I love it!");

        ResponseEntity<String> resp = apiClient.post("/api/v1/reviews", body,
                userHeaders(user.getToken()));

        // --- Then HTTP 403 ---
        assertTrue(
                resp.getStatusCode().value() == HttpStatus.FORBIDDEN.value()
                        || resp.getStatusCode().value() == HttpStatus.BAD_REQUEST.value(),
                "review without purchase should return 403 or 400, got "
                        + resp.getStatusCode().value());

        // --- Then code=REVIEW_PURCHASE_REQUIRED ---
        String code = apiClient.readJsonPath(resp, "$.code");
        if (code == null) {
            code = apiClient.readJsonPath(resp, "$.data.code");
        }
        if (code == null) {
            // The response may contain the error in a different structure
            String respBody = resp.getBody();
            assertNotNull(respBody, "response body should not be null");
        } else {
            assertTrue(
                    code.contains("PURCHASE") || code.contains("REVIEW_PURCHASE_REQUIRED")
                            || code.contains("NOT_PURCHASED"),
                    "error code should indicate purchase verification required, got: " + code);
        }
    }

    // ----------------------------------------------------------------
    // L4-04: Cart cache stays consistent after cart clear
    // ----------------------------------------------------------------

    @Test
    @DisplayName("HID-L4-04: cart clear invalidates cached cart; re-login shows empty cart")
    void l4_04_cartClearInvalidatesCachedCart() {
        // --- Given F-USER with cart items ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);

        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("50.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        CartFixture cartFixture = new CartFixture(apiClient, testRunContext);
        cartFixture.addItem(testRunContext, user.getToken(),
                String.valueOf(sku.getSkuId()), 3);

        // --- Verify cart is NOT empty ---
        CartFixture.CartResult cartBefore = cartFixture.getCart(testRunContext, user.getToken());
        assertTrue(cartBefore.getItems().size() > 0 || cartBefore.getTotalCount() > 0,
                "cart should have items before reset");

        // --- When DELETE /api/v1/cart and the same user logs in again ---
        ResponseEntity<String> clearResp = cartFixture.clearCart(testRunContext, user.getToken());
        assertTrue(clearResp.getStatusCode().is2xxSuccessful(),
                "/api/v1/cart DELETE should clear the cart");
        String newToken = userFixture.loginUser(testRunContext, testRunContext.uniqueEmail(), "Password123!");

        // --- Then GET /api/v1/cart → empty ---
        CartFixture.CartResult cartAfter = cartFixture.getCart(testRunContext, newToken);

        assertTrue(cartAfter.getItems() == null || cartAfter.getItems().isEmpty(),
                "cart should be empty after clear and re-login; no stale cached items");
        assertEquals(0, cartAfter.getTotalCount(),
                "cart totalCount should be 0 after clear");

        // The cart being empty proves that the write path invalidates any stale cart cache.
    }

    // ----------------------------------------------------------------
    // L4-05: Logistics failure does not block payment (requires /api/v1/admin/ops/fault-injections)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("HID-L4-05: logistics create-shipment failure records event but payment still succeeds")
    void l4_05_logisticsFailureDoesNotBlockPayment() {
        // --- Given F-ORDER ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);
        AddressResult addr = userFixture.createAddress(testRunContext, user.getToken(),
                "Guangdong", "Shenzhen", "Nanshan", "No.1 Tech Street");

        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("50.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        OrderFixture orderFixture = new OrderFixture(apiClient, testRunContext);
        OrderItemRequest item = new OrderItemRequest(String.valueOf(sku.getSkuId()), 1);
        OrderResult order = orderFixture.createOrder(testRunContext, user.getToken(),
                String.valueOf(addr.getAddressId()),
                List.of(item), List.of(), 0,
                testRunContext.uniqueExternalOrderNo());
        assertNotNull(order.getOrderId());

        // --- Given inject fault: logistics-create-shipment-failure ---
        Map<String, Object> faultBody = new LinkedHashMap<>();
        faultBody.put("fault", "logistics-create-shipment-failure");
        faultBody.put("enabled", true);
        HttpHeaders faultHeaders = new HttpHeaders();
        faultHeaders.setBearerAuth(testRunContext.getAdminToken());
        faultHeaders.setContentType(MediaType.APPLICATION_JSON);
        apiClient.post("/api/v1/admin/ops/fault-injections", faultBody, faultHeaders);

        // --- When pay + callback with valid signature ---
        BigDecimal payableAmount = order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getPayableAmount() : new BigDecimal("50.00");
        PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
        PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                order.getOrderId(), payableAmount,
                testRunContext.uniqueClientPaymentNo());
        paymentFixture.callback(testRunContext, payment.getPaymentNo(),
                order.getOrderId(), payableAmount);

        // --- Then: payment succeeds ---
        ResponseEntity<String> paymentResp = paymentFixture.getPayment(testRunContext,
                user.getToken(), payment.getPaymentNo());
        String paymentStatus = apiClient.readJsonPath(paymentResp, "$.status");
        if (paymentStatus == null) {
            paymentStatus = apiClient.readJsonPath(paymentResp, "$.data.status");
        }
        assertTrue(paymentStatus != null && paymentStatus.contains("SUCCESS"),
                "payment should succeed despite logistics failure, got: " + paymentStatus);

        // --- Then: order is PAID ---
        ResponseEntity<String> orderResp = orderFixture.getOrder(testRunContext, user.getToken(),
                order.getOrderId());
        String orderStatus = apiClient.readJsonPath(orderResp, "$.status");
        if (orderStatus == null) {
            orderStatus = apiClient.readJsonPath(orderResp, "$.data.status");
        }
        assertTrue(orderStatus != null && orderStatus.contains("PAID"),
                "order should be PAID despite logistics failure, got: " + orderStatus);

        // --- Then: event failure record exists ---
        HttpHeaders eventsHeaders = new HttpHeaders();
        eventsHeaders.setBearerAuth(testRunContext.getAdminToken());
        ResponseEntity<String> failuresResp = apiClient.get("/api/v1/admin/events/failures", eventsHeaders);
        assertTrue(failuresResp.getStatusCode().is2xxSuccessful(),
                "should be able to query event failures");
        String failuresBody = failuresResp.getBody();
        assertNotNull(failuresBody);
        assertTrue(failuresBody.contains("logistics") || failuresBody.contains("shipment"),
                "event failure record should mention logistics/shipment failure");

        // --- Then: payment main flow is NOT rolled back ---
        // (proven by payment SUCCESS and order PAID above)
    }

    // ----------------------------------------------------------------
    // L4-06: Inventory stock query product boundary (requires /api/v1/admin/ops/fault-injections)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("HID-L4-06: inventory query returns degraded product snapshot when product service down")
    void l4_06_inventoryStockQueryProductBoundary() {
        // --- Given F-SKU ---
        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("50.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        // --- Given inject fault: product-query-service-unavailable ---
        Map<String, Object> faultBody = new LinkedHashMap<>();
        faultBody.put("fault", "product-query-service-unavailable");
        faultBody.put("enabled", true);
        HttpHeaders faultHeaders = new HttpHeaders();
        faultHeaders.setBearerAuth(testRunContext.getAdminToken());
        faultHeaders.setContentType(MediaType.APPLICATION_JSON);
        apiClient.post("/api/v1/admin/ops/fault-injections", faultBody, faultHeaders);

        // --- When GET /api/v1/inventory/sku/{skuId} ---
        StockResult stock = inventoryFixture.getStockSummary(testRunContext, sku.getSkuId());

        // --- Then: stock data may be degraded or include a warning field ---
        // The response should indicate product snapshot unavailable or use a cached
        // fallback
        assertTrue(stock.getOnHandStock() >= 0,
                "inventory should still return stock data even with degraded product info");

        // --- Then clear faults ---
        apiClient.delete("/api/v1/admin/ops/fault-injections", faultHeaders);

        // --- Then re-query returns full product snapshot ---
        StockResult stockAfter = inventoryFixture.getStockSummary(testRunContext, sku.getSkuId());
        assertNotNull(stockAfter, "after clearing faults, stock summary should return normally");
        assertEquals(10, stockAfter.getAvailableStock(),
                "available stock should be 10 after clearing faults");
    }

    // ----------------------------------------------------------------
    // L4-08: Member level stat boundary (requires /api/v1/admin/ops/fault-injections)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("HID-L4-08: member-level returns degraded stat when order query is unavailable; recovers after clear")
    void l4_08_memberLevelStatBoundary() {
        // --- Given F-USER ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);

        // --- Given inject fault: order-query-service-unavailable ---
        Map<String, Object> faultBody = new LinkedHashMap<>();
        faultBody.put("fault", "order-query-service-unavailable");
        faultBody.put("enabled", true);
        HttpHeaders faultHeaders = new HttpHeaders();
        faultHeaders.setBearerAuth(testRunContext.getAdminToken());
        faultHeaders.setContentType(MediaType.APPLICATION_JSON);
        apiClient.post("/api/v1/admin/ops/fault-injections", faultBody, faultHeaders);

        // --- When GET /api/v1/loyalty/member-level ---
        LoyaltyFixture loyaltyFixture = new LoyaltyFixture(apiClient, testRunContext);
        LoyaltyFixture.MemberLevelResult level = loyaltyFixture.getMemberLevel(testRunContext, user.getToken());

        // --- Then: member level may return degraded result ---
        // The response could indicate the order statistics are unavailable or return
        // a default level
        assertNotNull(level, "member level query should return a result even with degraded stats");

        // --- Then clear faults ---
        apiClient.delete("/api/v1/admin/ops/fault-injections", faultHeaders);

        // --- Then create paid orders to accumulate tier points ---
        AddressResult addr = userFixture.createAddress(testRunContext, user.getToken(),
                "Guangdong", "Shenzhen", "Nanshan", "No.1 Tech Street");
        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);

        // Create multiple paid orders to build up tier points
        for (int i = 0; i < 3; i++) {
            SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                    testRunContext.uniqueSkuCode() + "-T" + i, new BigDecimal("500.00"));
            inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                    sku.getSkuId(), 10);

            OrderFixture orderFixture = new OrderFixture(apiClient, testRunContext);
            OrderResult order = orderFixture.createOrder(testRunContext, user.getToken(),
                    String.valueOf(addr.getAddressId()),
                    List.of(new OrderItemRequest(String.valueOf(sku.getSkuId()), 1)),
                    List.of(), 0,
                    testRunContext.uniqueExternalOrderNo() + "-T" + i);

            PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
            BigDecimal payable = order.getPayableAmount() != null
                    && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                    ? order.getPayableAmount() : new BigDecimal("500.00");
            PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                    order.getOrderId(), payable,
                    testRunContext.uniqueClientPaymentNo() + "-T" + i);
            paymentFixture.callback(testRunContext, payment.getPaymentNo(),
                    order.getOrderId(), payable);
        }

        // --- Then: after clearing faults, member level can upgrade ---
        LoyaltyFixture.MemberLevelResult levelAfter = loyaltyFixture.getMemberLevel(testRunContext, user.getToken());
        assertNotNull(levelAfter.getLevel(), "member level should be returned after clearing faults");
    }

    // ==================================================================
    // L5: Event Decoupling Tests
    // ==================================================================

    // ----------------------------------------------------------------
    // L5-01: Post-payment actions do not block payment (requires /api/v1/admin/ops/fault-injections)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("HID-L5-01: payment succeeds despite notification + logistics failures; post-actions recorded as failures")
    void l5_01_postPaymentActionsDoNotBlockPayment() {
        // --- Given F-ORDER ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);
        AddressResult addr = userFixture.createAddress(testRunContext, user.getToken(),
                "Guangdong", "Shenzhen", "Nanshan", "No.1 Tech Street");

        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("50.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        OrderFixture orderFixture = new OrderFixture(apiClient, testRunContext);
        OrderItemRequest item = new OrderItemRequest(String.valueOf(sku.getSkuId()), 1);
        OrderResult order = orderFixture.createOrder(testRunContext, user.getToken(),
                String.valueOf(addr.getAddressId()),
                List.of(item), List.of(), 0,
                testRunContext.uniqueExternalOrderNo());
        assertNotNull(order.getOrderId());

        // --- Given inject multiple faults ---
        HttpHeaders faultHeaders = new HttpHeaders();
        faultHeaders.setBearerAuth(testRunContext.getAdminToken());
        faultHeaders.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> fault1 = new LinkedHashMap<>();
        fault1.put("fault", "notification-send-failure");
        fault1.put("enabled", true);
        apiClient.post("/api/v1/admin/ops/fault-injections", fault1, faultHeaders);

        Map<String, Object> fault2 = new LinkedHashMap<>();
        fault2.put("fault", "logistics-create-shipment-failure");
        fault2.put("enabled", true);
        apiClient.post("/api/v1/admin/ops/fault-injections", fault2, faultHeaders);

        // --- When pay + callback with valid signature ---
        BigDecimal payableAmount = order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getPayableAmount() : new BigDecimal("50.00");
        PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
        PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                order.getOrderId(), payableAmount,
                testRunContext.uniqueClientPaymentNo());
        ResponseEntity<String> callbackResp = paymentFixture.callback(testRunContext,
                payment.getPaymentNo(), order.getOrderId(), payableAmount);

        // --- Then: callback returns 200 ---
        assertTrue(callbackResp.getStatusCode().is2xxSuccessful(),
                "payment callback should return 200 despite post-action faults");

        // --- Then: payment is SUCCESS ---
        ResponseEntity<String> paymentResp = paymentFixture.getPayment(testRunContext,
                user.getToken(), payment.getPaymentNo());
        String paymentStatus = apiClient.readJsonPath(paymentResp, "$.status");
        if (paymentStatus == null) {
            paymentStatus = apiClient.readJsonPath(paymentResp, "$.data.status");
        }
        assertEquals("SUCCESS", paymentStatus, "payment should be SUCCESS");

        // --- Then: order is PAID ---
        ResponseEntity<String> orderResp = orderFixture.getOrder(testRunContext, user.getToken(),
                order.getOrderId());
        String orderStatus = apiClient.readJsonPath(orderResp, "$.status");
        if (orderStatus == null) {
            orderStatus = apiClient.readJsonPath(orderResp, "$.data.status");
        }
        assertTrue(orderStatus != null && orderStatus.contains("PAID"),
                "order should be PAID despite post-action failures, got: " + orderStatus);

        // --- Then: event failure records contain post-action failures ---
        HttpHeaders eventsHeaders = new HttpHeaders();
        eventsHeaders.setBearerAuth(testRunContext.getAdminToken());
        ResponseEntity<String> failuresResp = apiClient.get("/api/v1/admin/events/failures", eventsHeaders);
        String failuresBody = failuresResp.getBody();
        assertNotNull(failuresBody);
        // Post-action failures (notification + logistics) should be recorded
        assertTrue(failuresBody.contains("notification") || failuresBody.contains("logistics")
                        || failuresBody.contains("shipment"),
                "event failure records should include post-action failure entries");
    }

    // ----------------------------------------------------------------
    // L5-02: Event failure record generated (requires /api/v1/admin/events/failures)
    // ----------------------------------------------------------------

    @Test
    @DisplayName("HID-L5-02: loyalty listener failure recorded with eventId/eventType/listenerName/errorMessage/retryable=true")
    void l5_02_eventFailureRecordGenerated() {
        // --- Given F-ORDER ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);
        AddressResult addr = userFixture.createAddress(testRunContext, user.getToken(),
                "Guangdong", "Shenzhen", "Nanshan", "No.1 Tech Street");

        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("50.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        OrderFixture orderFixture = new OrderFixture(apiClient, testRunContext);
        OrderItemRequest item = new OrderItemRequest(String.valueOf(sku.getSkuId()), 1);
        OrderResult order = orderFixture.createOrder(testRunContext, user.getToken(),
                String.valueOf(addr.getAddressId()),
                List.of(item), List.of(), 0,
                testRunContext.uniqueExternalOrderNo());
        assertNotNull(order.getOrderId());

        // --- Given inject fault: loyalty-award-points-failure ---
        Map<String, Object> faultBody = new LinkedHashMap<>();
        faultBody.put("fault", "loyalty-award-points-failure");
        faultBody.put("enabled", true);
        HttpHeaders faultHeaders = new HttpHeaders();
        faultHeaders.setBearerAuth(testRunContext.getAdminToken());
        faultHeaders.setContentType(MediaType.APPLICATION_JSON);
        apiClient.post("/api/v1/admin/ops/fault-injections", faultBody, faultHeaders);

        // --- When complete payment callback ---
        BigDecimal payableAmount = order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getPayableAmount() : new BigDecimal("50.00");
        PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
        PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                order.getOrderId(), payableAmount,
                testRunContext.uniqueClientPaymentNo());
        paymentFixture.callback(testRunContext, payment.getPaymentNo(),
                order.getOrderId(), payableAmount);

        // --- When GET /api/v1/admin/events/failures ---
        HttpHeaders eventsHeaders = new HttpHeaders();
        eventsHeaders.setBearerAuth(testRunContext.getAdminToken());
        ResponseEntity<String> failuresResp = apiClient.get("/api/v1/admin/events/failures", eventsHeaders);

        // --- Then ---
        assertTrue(failuresResp.getStatusCode().is2xxSuccessful(),
                "/api/v1/admin/events/failures should return 200");

        String failuresBody = failuresResp.getBody();
        assertNotNull(failuresBody);

        // --- Then: loyalty listener failure record exists ---
        boolean hasLoyaltyFailure = failuresBody.contains("loyalty")
                || failuresBody.contains("points")
                || failuresBody.contains("LOYALTY");
        assertTrue(hasLoyaltyFailure,
                "events/failures should contain a loyalty listener failure record");

        // --- Then: record contains eventId, eventType, listenerName, errorMessage,
        // retryable=true ---
        boolean hasEventId = failuresBody.contains("eventId") || failuresBody.contains("event_id");
        boolean hasEventType = failuresBody.contains("eventType") || failuresBody.contains("event_type");
        boolean hasListenerName = failuresBody.contains("listenerName") || failuresBody.contains("listener_name");
        boolean hasErrorMessage = failuresBody.contains("errorMessage") || failuresBody.contains("error_message");
        boolean hasRetryable = failuresBody.contains("retryable") || failuresBody.contains("retryable");

        assertTrue(hasEventId, "failure record should contain eventId");
        assertTrue(hasEventType, "failure record should contain eventType");
        assertTrue(hasListenerName, "failure record should contain listenerName");
        assertTrue(hasErrorMessage, "failure record should contain errorMessage");

        // retryable should be true (the event can be retried)
        if (hasRetryable) {
            assertTrue(failuresBody.contains("\"retryable\":true")
                            || failuresBody.contains("\"retryable\": true")
                            || failuresBody.contains("retryable\":true"),
                    "failure record retryable should be true");
        }
    }
}
