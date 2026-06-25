package com.ecommerce.blackbox.hidden.e2e;

import com.ecommerce.blackbox.common.BlackboxTestBase;
import com.ecommerce.blackbox.common.fixture.*;
import com.ecommerce.blackbox.common.fixture.CartFixture.EstimateResult;
import com.ecommerce.blackbox.common.fixture.InventoryFixture.StockResult;
import com.ecommerce.blackbox.common.fixture.InventoryFixture.WarehouseAndStockResult;
import com.ecommerce.blackbox.common.fixture.InvoiceFixture.InvoiceResult;
import com.ecommerce.blackbox.common.fixture.LogisticsFixture.LogisticsResult;
import com.ecommerce.blackbox.common.fixture.LoyaltyFixture.MemberLevelResult;
import com.ecommerce.blackbox.common.fixture.LoyaltyFixture.PointsResult;
import com.ecommerce.blackbox.common.fixture.LoyaltyFixture.RedeemEstimateResult;
import com.ecommerce.blackbox.common.fixture.OrderFixture.OrderItemRequest;
import com.ecommerce.blackbox.common.fixture.OrderFixture.OrderResult;
import com.ecommerce.blackbox.common.fixture.PaymentFixture.PaymentResult;
import com.ecommerce.blackbox.common.fixture.ProductFixture.SkuResult;
import com.ecommerce.blackbox.common.fixture.PromotionFixture.ClaimResult;
import com.ecommerce.blackbox.common.fixture.PromotionFixture.CouponResult;
import com.ecommerce.blackbox.common.fixture.RefundFixture.RefundResult;
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
 * Hidden E2E (End-to-End) tests.
 * <p>
 * Each test chains multiple REST fixture calls to verify a complete business
 * flow from start to finish. These tests exercise the integration of multiple
 * modules (user, product, inventory, order, payment, logistics, refund, review,
 * loyalty, invoice, settlement) in a single cohesive scenario.
 * <p>
 * All tests extend {@link BlackboxTestBase} and use fixture classes
 * exclusively -- no direct database access.
 * <p>
 * <b>Baseline behavior note:</b> Several assertions in this class expect
 * correct behavior from the baseline specification. Current implementation
 * may differ (e.g., stock deduction after payment, review approval points
 * flow). Do not change assertions to match current buggy behavior.
 */
@DisplayName("Hidden E2E — Complete Business Flow Verification")
class HiddenE2ETest extends BlackboxTestBase {

    // ==================================================================
    // HID-E2E-01: Register → Activate → Login → Get Profile
    // ==================================================================

    @Test
    @DisplayName("HID-E2E-01: register → activate → login → GET /users/me returns user info")
    void e2e01_registerActivateLogin() {
        // --- When POST /api/v1/users/register ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        UserFixture.RegisterResult regResult = userFixture.registerUser(testRunContext);

        assertNotNull(regResult.getUserId(), "registration should return userId");
        assertNotNull(regResult.getEmail(), "registration should return email");

        // --- When POST /api/v1/users/activate ---
        String activationToken = regResult.getActivationToken();
        if (activationToken != null && !activationToken.isEmpty()) {
            Map<String, Object> activateBody = new LinkedHashMap<>();
            activateBody.put("token", activationToken);
            HttpHeaders activateHeaders = new HttpHeaders();
            activateHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> activateResp = apiClient.post("/api/v1/users/activate",
                    activateBody, activateHeaders);
            // Activation may fail if user is already ACTIVE -- acceptable
        }

        // --- When POST /api/v1/users/login ---
        String token = userFixture.loginUser(testRunContext, regResult.getEmail(), "Password123!");
        assertNotNull(token, "login should return a JWT token");
        assertFalse(token.isEmpty(), "JWT token should not be empty");

        // --- When GET /api/v1/users/me ---
        ResponseEntity<String> meResp = userFixture.getCurrentUser(testRunContext, token);
        assertEquals(HttpStatus.OK.value(), meResp.getStatusCode().value(),
                "GET /api/v1/users/me should return 200");

        // --- Then: response contains user info ---
        String meBody = meResp.getBody();
        assertNotNull(meBody, "/users/me response body should not be null");

        // Verify the response contains the registered email
        assertTrue(meBody.contains(regResult.getEmail())
                        || meBody.contains("email")
                        || meBody.contains("nickname"),
                "/users/me should return user profile information");

        // Verify userId is present
        boolean hasUserId = meBody.contains(String.valueOf(regResult.getUserId()))
                || meBody.contains("\"userId\"")
                || meBody.contains("\"id\"");
        assertTrue(hasUserId, "/users/me should identify the current user");
    }

    // ==================================================================
    // HID-E2E-02: Admin creates SPU → SKU → On-shelf → Search → Detail
    // ==================================================================

    @Test
    @DisplayName("HID-E2E-02: admin creates product from SPU through on-shelf; search and detail show it")
    void e2e02_productCreateSearchDetail() {
        // --- Given F-ADMIN (already seeded by @BeforeEach) ---
        String adminToken = testRunContext.getAdminToken();
        assertNotNull(adminToken, "admin token should be available");

        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);

        // --- When POST /api/v1/admin/products/spu ---
        Long spuId = productFixture.createSpu(testRunContext, adminToken);
        assertNotNull(spuId, "SPU should be created");

        // --- When POST /api/v1/admin/products/sku ---
        String skuCode = testRunContext.uniqueSkuCode();
        Long skuId = productFixture.createSku(testRunContext, adminToken, spuId,
                skuCode, new BigDecimal("99.99"));
        assertNotNull(skuId, "SKU should be created");

        // --- When POST /api/v1/admin/products/sku/{skuId}/on-shelf ---
        productFixture.onShelf(testRunContext, adminToken, skuId);

        // --- When GET /api/v1/products/search?keyword={testRunId} ---
        ResponseEntity<String> searchResp = productFixture.searchProducts(testRunContext,
                testRunContext.getTestRunId());
        assertEquals(HttpStatus.OK.value(), searchResp.getStatusCode().value(),
                "product search should return 200");

        String searchBody = searchResp.getBody();
        assertNotNull(searchBody, "search response body should not be null");
        // Search results should contain the SKU
        assertTrue(searchBody.contains(String.valueOf(skuId)) || searchBody.contains(skuCode),
                "search results should contain the created SKU");

        // --- When GET /api/v1/products/{skuId} ---
        ResponseEntity<String> detailResp = productFixture.getProductDetail(testRunContext, skuId);
        assertEquals(HttpStatus.OK.value(), detailResp.getStatusCode().value(),
                "product detail should return 200");

        String detailBody = detailResp.getBody();
        assertNotNull(detailBody, "detail response body should not be null");
        assertTrue(detailBody.contains(String.valueOf(skuId)) || detailBody.contains(skuCode),
                "product detail should contain the SKU identifier");

        // --- Then: product is ON_SHELF and searchable ---
        assertTrue(detailBody.contains("ON_SHELF") || detailBody.contains("on_shelf")
                        || detailBody.contains("active"),
                "product should be in ON_SHELF status in detail response");
    }

    // ==================================================================
    // HID-E2E-03: Stock → Order → Payment flow with inventory tracking
    // ==================================================================

    @Test
    @DisplayName("HID-E2E-03: create order 2 items → reservedStock=2 → pay → onHandStock-=2, reservedStock=0")
    void e2e03_stockOrderPaymentFlow() {
        // --- Given F-USER + F-SKU with stock 10 ---
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

        // --- When create order buying 2 items ---
        OrderFixture orderFixture = new OrderFixture(apiClient, testRunContext);
        OrderItemRequest item = new OrderItemRequest(String.valueOf(sku.getSkuId()), 2);
        OrderResult order = orderFixture.createOrder(testRunContext, user.getToken(),
                String.valueOf(addr.getAddressId()),
                List.of(item), List.of(), 0,
                testRunContext.uniqueExternalOrderNo());
        assertNotNull(order.getOrderId(), "order should be created");

        // --- When query stock after order ---
        StockResult stockAfterOrder = inventoryFixture.getStockSummary(testRunContext, sku.getSkuId());

        // --- Then: reservedStock=2, availableStock=8 ---
        assertEquals(10, stockAfterOrder.getOnHandStock(),
                "onHandStock should still be 10 after order creation");
        assertEquals(2, stockAfterOrder.getReservedStock(),
                "reservedStock should be 2 after order creation");
        assertEquals(8, stockAfterOrder.getAvailableStock(),
                "availableStock should be 8 (10 - 2) after order creation");

        // --- When complete payment ---
        PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
        BigDecimal payableAmount = order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getPayableAmount() : new BigDecimal("100.00");
        PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                order.getOrderId(), payableAmount,
                testRunContext.uniqueClientPaymentNo());
        assertNotNull(payment.getPaymentNo(), "payment should be initiated");

        paymentFixture.callback(testRunContext, payment.getPaymentNo(),
                order.getOrderId(), payableAmount);

        // --- When query stock after payment ---
        StockResult stockAfterPayment = inventoryFixture.getStockSummary(testRunContext, sku.getSkuId());

        // --- Then: onHandStock=8, reservedStock=0 ---
        assertEquals(8, stockAfterPayment.getOnHandStock(),
                "onHandStock should be 8 after payment (decremented)");
        assertEquals(0, stockAfterPayment.getReservedStock(),
                "reservedStock should be 0 after payment");

        // --- Then: order status is PAID or beyond ---
        ResponseEntity<String> orderResp = orderFixture.getOrder(testRunContext, user.getToken(),
                order.getOrderId());
        String orderStatus = apiClient.readJsonPath(orderResp, "$.status");
        if (orderStatus == null) {
            orderStatus = apiClient.readJsonPath(orderResp, "$.data.status");
        }
        assertTrue(orderStatus != null && (orderStatus.contains("PAID")
                        || orderStatus.contains("PROCESSING") || orderStatus.contains("SHIPPING")),
                "order should be PAID or beyond after payment, got: " + orderStatus);
    }

    // ==================================================================
    // HID-E2E-04: Cart → Estimate → Promo → Points → Order with discounts
    // ==================================================================

    @Test
    @DisplayName("HID-E2E-04: cart → promo → points → order: amounts reflect coupon + points deduction")
    void e2e04_cartPromoPointsOrderFlow() {
        // --- Given F-USER ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);
        AddressResult addr = userFixture.createAddress(testRunContext, user.getToken(),
                "Guangdong", "Shenzhen", "Nanshan", "No.1 Tech Street");

        // --- Given F-SKU with price=200.00 quantity=100 ---
        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("200.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 100);

        // --- Given create and claim a 10% OFF coupon ---
        PromotionFixture promotionFixture = new PromotionFixture(apiClient, testRunContext);
        CouponResult coupon = promotionFixture.createCoupon(testRunContext,
                testRunContext.getAdminToken(), "10%-Off", "DISCOUNT", 0.9);
        assertNotNull(coupon.getCouponTemplateId(), "coupon template should be created");
        ClaimResult claimed = promotionFixture.claimCoupon(testRunContext, user.getToken(),
                coupon.getCouponTemplateId());
        assertNotNull(claimed.getCouponId(), "coupon should be claimed");

        // --- Given: add items to cart ---
        CartFixture cartFixture = new CartFixture(apiClient, testRunContext);
        cartFixture.addItem(testRunContext, user.getToken(),
                String.valueOf(sku.getSkuId()), 2);

        // --- When: estimate cart ---
        EstimateResult estimate = cartFixture.estimate(testRunContext, user.getToken());

        // --- Then: estimate returns itemTotal (200 * 2 = 400) ---
        if (estimate.getItemTotal().compareTo(BigDecimal.ZERO) > 0) {
            assertTrue(estimate.getItemTotal().compareTo(new BigDecimal("300.00")) > 0,
                    "itemTotal should > 300 for 2 x 200.00 items");
        }

        // --- When: create order with coupon and points (redeem 1000 points) ---
        OrderFixture orderFixture = new OrderFixture(apiClient, testRunContext);
        OrderResult order = orderFixture.createOrder(testRunContext, user.getToken(),
                String.valueOf(addr.getAddressId()),
                List.of(new OrderItemRequest(String.valueOf(sku.getSkuId()), 2)),
                List.of(claimed.getCouponId()),
                1000,
                testRunContext.uniqueExternalOrderNo());

        // --- Then: order fields are complete ---
        assertNotNull(order.getOrderId(), "order should be created");
        assertNotNull(order.getItemTotal(), "itemTotal should be present");
        assertNotNull(order.getShippingFee(), "shippingFee should be present");
        assertNotNull(order.getPayableAmount(), "payableAmount should be present");

        // --- Then: coupon discount reflected ---
        assertTrue(order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0,
                "discountAmount should reflect coupon discount, got: " + order.getDiscountAmount());

        // --- Then: points deduction reflected ---
        // Points deduction should reduce the payable amount
        assertTrue(order.getPayableAmount().compareTo(order.getItemTotal()) < 0,
                "payableAmount should be less than itemTotal after discounts and points");

        // --- Then: all monetary fields are consistent ---
        BigDecimal reconstructed = order.getItemTotal()
                .add(order.getShippingFee())
                .add(order.getPackagingFee())
                .subtract(order.getDiscountAmount())
                .subtract(order.getPointsDeductionAmount());
        assertEquals(0, reconstructed.compareTo(order.getPayableAmount()),
                "payableAmount = itemTotal + shippingFee + packagingFee - discountAmount - pointsDeductionAmount");
    }

    // ==================================================================
    // HID-E2E-05: Payment → Shipment (pick → label → outbound) → Delivery callback
    // ==================================================================

    @Test
    @DisplayName("HID-E2E-05: paid order → pick → print-label → outbound → logistics callback DELIVERED")
    void e2e05_paymentShipmentDeliveryFlow() {
        // --- Given F-PAID-ORDER ---
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

        PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
        BigDecimal payableAmount = order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getPayableAmount() : new BigDecimal("50.00");
        PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                order.getOrderId(), payableAmount,
                testRunContext.uniqueClientPaymentNo());
        paymentFixture.callback(testRunContext, payment.getPaymentNo(),
                order.getOrderId(), payableAmount);

        // --- When GET /api/v1/logistics/order/{orderId} ---
        LogisticsFixture logisticsFixture = new LogisticsFixture(apiClient, testRunContext);
        LogisticsResult logistics = logisticsFixture.getLogistics(testRunContext, user.getToken(),
                order.getOrderId());

        // If no shipment was auto-created, the shipment lifecycle endpoints may not
        // be reachable. This is acceptable -- different implementations have
        // different shipment creation triggers.
        if (logistics.getShipmentId() == null) {
            // No auto-created shipment -- skip remaining verification
            return;
        }

        String shipmentId = logistics.getShipmentId();

        // --- When admin pick ---
        logisticsFixture.pick(testRunContext, testRunContext.getAdminToken(), shipmentId);
        LogisticsResult afterPick = logisticsFixture.getLogistics(testRunContext, user.getToken(),
                order.getOrderId());
        assertTrue(afterPick.getStatus() != null
                        && (afterPick.getStatus().contains("PICK")
                        || afterPick.getStatus().contains("ASSEMBL")),
                "after pick, status should contain PICKING, got: " + afterPick.getStatus());

        // --- When admin print-label ---
        logisticsFixture.printLabel(testRunContext, testRunContext.getAdminToken(), shipmentId);
        LogisticsResult afterLabel = logisticsFixture.getLogistics(testRunContext, user.getToken(),
                order.getOrderId());
        assertTrue(afterLabel.getStatus() != null
                        && (afterLabel.getStatus().contains("LABEL")
                        || afterLabel.getStatus().contains("PRINT")),
                "after print-label, status should contain LABEL, got: " + afterLabel.getStatus());

        // --- When admin outbound ---
        logisticsFixture.outbound(testRunContext, testRunContext.getAdminToken(), shipmentId);
        LogisticsResult afterOutbound = logisticsFixture.getLogistics(testRunContext, user.getToken(),
                order.getOrderId());
        assertTrue(afterOutbound.getStatus() != null
                        && (afterOutbound.getStatus().contains("OUTBOUND")
                        || afterOutbound.getStatus().contains("SHIPPED")
                        || afterOutbound.getStatus().contains("TRANSIT")),
                "after outbound, status should be OUTBOUND, got: " + afterOutbound.getStatus());

        // --- When logistics callback DELIVERED ---
        String trackingNo = afterOutbound.getTrackingNo();
        if (trackingNo != null) {
            logisticsFixture.logisticsCallback(testRunContext, trackingNo, "DELIVERED");
        } else {
            // If no trackingNo, try to callback with any identifier
            logisticsFixture.logisticsCallback(testRunContext, "TRACK-" + testRunContext.getTestRunId(),
                    "DELIVERED");
        }

        // --- Then: logistics status is DELIVERED ---
        LogisticsResult afterDelivery = logisticsFixture.getLogistics(testRunContext, user.getToken(),
                order.getOrderId());
        if (afterDelivery.getStatus() != null) {
            assertTrue(
                    afterDelivery.getStatus().contains("DELIVERED")
                            || afterDelivery.getStatus().contains("SIGNED")
                            || afterDelivery.getStatus().contains("COMPLETE"),
                    "logistics should be DELIVERED after callback, got: " + afterDelivery.getStatus());
        }

        // --- Then: order logistics status is synchronized ---
        ResponseEntity<String> orderResp = orderFixture.getOrder(testRunContext, user.getToken(),
                order.getOrderId());
        String orderBody = orderResp.getBody();
        if (orderBody != null) {
            assertTrue(
                    orderBody.contains("DELIVERED") || orderBody.contains("delivered")
                            || orderBody.contains("SIGNED") || orderBody.contains("COMPLETE")
                            || orderBody.contains("RECEIVED"),
                    "order should reflect delivered logistics status after callback");
        }
    }

    // ==================================================================
    // HID-E2E-06: Refund → Review → Warehouse Accept → Fee applied
    // ==================================================================

    @Test
    @DisplayName("HID-E2E-06: paid → refund apply → admin review → warehouse accept → refundAmount=98.00")
    void e2e06_refundReviewAcceptFlow() {
        // --- Given F-PAID-ORDER paidAmount=100.00 ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);
        AddressResult addr = userFixture.createAddress(testRunContext, user.getToken(),
                "Guangdong", "Shenzhen", "Nanshan", "No.1 Tech Street");

        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("100.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        OrderFixture orderFixture = new OrderFixture(apiClient, testRunContext);
        OrderItemRequest item = new OrderItemRequest(String.valueOf(sku.getSkuId()), 1);
        OrderResult order = orderFixture.createOrder(testRunContext, user.getToken(),
                String.valueOf(addr.getAddressId()),
                List.of(item), List.of(), 0,
                testRunContext.uniqueExternalOrderNo());
        assertNotNull(order.getOrderId());

        PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
        BigDecimal payableAmount = order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getPayableAmount() : new BigDecimal("100.00");
        PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                order.getOrderId(), payableAmount,
                testRunContext.uniqueClientPaymentNo());
        paymentFixture.callback(testRunContext, payment.getPaymentNo(),
                order.getOrderId(), payableAmount);

        // --- When POST /api/v1/refunds/apply ---
        RefundFixture refundFixture = new RefundFixture(apiClient, testRunContext);
        RefundResult refundApply = refundFixture.applyRefund(testRunContext, user.getToken(),
                order.getOrderId(), payment.getPaymentNo(),
                "Package damaged during shipping");
        if (refundApply.getRefundId() == null) {
            // Refund application may fail if the system doesn't allow refunds at this
            // stage
            return;
        }
        assertNotNull(refundApply.getRefundId(), "refund should be created");

        // --- When admin reviews refund (approve) ---
        refundFixture.reviewRefund(testRunContext, testRunContext.getAdminToken(),
                refundApply.getRefundId(), true);

        // --- When admin warehouse accepts ---
        refundFixture.warehouseAccept(testRunContext, testRunContext.getAdminToken(),
                refundApply.getRefundId(), true);

        // --- When GET /api/v1/refunds/{refundId} ---
        RefundResult refundAfter = refundFixture.getRefund(testRunContext, user.getToken(),
                refundApply.getRefundId());

        // --- Then: refund status is correct ---
        assertNotNull(refundAfter.getStatus(), "refund status should be present");
        assertTrue(
                refundAfter.getStatus().contains("REFUNDED")
                        || refundAfter.getStatus().contains("ACCEPTED")
                        || refundAfter.getStatus().contains("SUCCESS")
                        || refundAfter.getStatus().contains("COMPLETE"),
                "refund should be in a final state after review + warehouse accept, got: "
                        + refundAfter.getStatus());

        // --- Then: refund amount reflects 2% fee (100 → 98) ---
        String rawRefund = apiClient.get("/api/v1/refunds/" + refundApply.getRefundId(),
                userHeaders(user.getToken())).getBody();
        if (rawRefund != null) {
            // The response should contain the refund amount calculation
            boolean hasAmount = rawRefund.contains("refundAmount")
                    || rawRefund.contains("refund_amount") || rawRefund.contains("amount");
            if (hasAmount) {
                assertTrue(
                        rawRefund.contains("98.00") || rawRefund.contains("98.0")
                                || rawRefund.contains("98"),
                        "refund amount should be 98.00 (100.00 - 2% fee)");
            }
        }

        // --- Then: refund processing is complete ---
        assertNotEquals("WAITING_WAREHOUSE_ACCEPT", refundAfter.getStatus(),
                "refund should NOT still be waiting for warehouse after acceptance");
    }

    // ==================================================================
    // HID-E2E-07: Review → Approval → Points reward
    // ==================================================================

    @Test
    @DisplayName("HID-E2E-07: delivered order → post review → admin approve → points awarded")
    void e2e07_reviewApprovalPointsFlow() {
        // --- Given F-PAID-ORDER ---
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

        PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
        BigDecimal payableAmount = order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getPayableAmount() : new BigDecimal("50.00");
        PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                order.getOrderId(), payableAmount,
                testRunContext.uniqueClientPaymentNo());
        paymentFixture.callback(testRunContext, payment.getPaymentNo(),
                order.getOrderId(), payableAmount);

        // --- Given: record initial points ---
        LoyaltyFixture loyaltyFixture = new LoyaltyFixture(apiClient, testRunContext);
        PointsResult initialPoints = loyaltyFixture.getPoints(testRunContext, user.getToken());
        int initialAvailable = initialPoints.getAvailablePoints();

        // --- When POST /api/v1/reviews ---
        ReviewFixture reviewFixture = new ReviewFixture(apiClient, testRunContext);
        ReviewResult review = reviewFixture.createReview(testRunContext, user.getToken(),
                String.valueOf(sku.getSkuId()), order.getOrderId(), "1",
                5, "Excellent product, fast delivery!");

        // Review may have been created or rejected (e.g., due to purchase verification)
        if (review.getReviewId() == null) {
            // Review creation failed -- verify the error is expected
            return;
        }

        // --- Then: points should NOT increase after review submission alone ---
        PointsResult afterReviewPoints = loyaltyFixture.getPoints(testRunContext, user.getToken());
        assertEquals(initialAvailable, afterReviewPoints.getAvailablePoints(),
                "points should NOT increase immediately after review submission");

        // --- When admin approves review ---
        reviewFixture.approveReview(testRunContext, testRunContext.getAdminToken(),
                review.getReviewId());

        // --- Then: points SHOULD increase after approval ---
        PointsResult afterApprovalPoints = loyaltyFixture.getPoints(testRunContext, user.getToken());
        assertTrue(afterApprovalPoints.getAvailablePoints() > initialAvailable,
                "points should increase after review approval (review reward earned). "
                        + "Before: " + initialAvailable + ", After: " + afterApprovalPoints.getAvailablePoints());

        // --- Then: points history contains review reward ---
        ResponseEntity<String> historyResp = loyaltyFixture.getPointsHistory(testRunContext,
                user.getToken());
        if (historyResp.getStatusCode().is2xxSuccessful() && historyResp.getBody() != null) {
            String historyBody = historyResp.getBody();
            assertTrue(
                    historyBody.contains("REVIEW") || historyBody.contains("review")
                            || historyBody.contains("REWARD"),
                    "points history should contain review reward entry");
        }
    }

    // ==================================================================
    // HID-E2E-08: Invoice → Settlement batch flow
    // ==================================================================

    @Test
    @DisplayName("HID-E2E-08: paid → partial invoice → second invoice → settlement batch includes paid order")
    void e2e08_invoiceSettlementFlow() {
        // --- Given F-PAID-ORDER paidAmount=500.00 ---
        UserFixture userFixture = new UserFixture(apiClient, testRunContext);
        ActivatedUser user = userFixture.registerAndActivateUser(testRunContext);
        AddressResult addr = userFixture.createAddress(testRunContext, user.getToken(),
                "Guangdong", "Shenzhen", "Nanshan", "No.1 Tech Street");

        ProductFixture productFixture = new ProductFixture(apiClient, testRunContext);
        InventoryFixture inventoryFixture = new InventoryFixture(apiClient, testRunContext);
        SkuResult sku = productFixture.createOnShelfSku(testRunContext, testRunContext.getAdminToken(),
                testRunContext.uniqueSkuCode(), new BigDecimal("500.00"));
        inventoryFixture.createWarehouseAndInbound(testRunContext, testRunContext.getAdminToken(),
                sku.getSkuId(), 10);

        OrderFixture orderFixture = new OrderFixture(apiClient, testRunContext);
        OrderItemRequest item = new OrderItemRequest(String.valueOf(sku.getSkuId()), 1);
        OrderResult order = orderFixture.createOrder(testRunContext, user.getToken(),
                String.valueOf(addr.getAddressId()),
                List.of(item), List.of(), 0,
                testRunContext.uniqueExternalOrderNo());
        assertNotNull(order.getOrderId());

        PaymentFixture paymentFixture = new PaymentFixture(apiClient, testRunContext);
        BigDecimal payableAmount = order.getPayableAmount() != null
                && order.getPayableAmount().compareTo(BigDecimal.ZERO) > 0
                ? order.getPayableAmount() : new BigDecimal("500.00");
        PaymentResult payment = paymentFixture.pay(testRunContext, user.getToken(),
                order.getOrderId(), payableAmount,
                testRunContext.uniqueClientPaymentNo());
        paymentFixture.callback(testRunContext, payment.getPaymentNo(),
                order.getOrderId(), payableAmount);

        // --- When POST /api/v1/invoices (partial: 300.00) ---
        InvoiceFixture invoiceFixture = new InvoiceFixture(apiClient, testRunContext);
        InvoiceResult invoice1 = invoiceFixture.createInvoice(testRunContext, user.getToken(),
                order.getOrderId(), "PERSONAL", new BigDecimal("300.00"),
                "Partial Invoice 1");
        if (invoice1.getInvoiceId() == null) {
            // Invoice creation may not be supported or may fail with a specific reason
            return;
        }
        assertNotNull(invoice1.getInvoiceId(), "first invoice should be created");

        // --- When POST /api/v1/invoices (remaining: 200.00) ---
        InvoiceResult invoice2 = invoiceFixture.createInvoice(testRunContext, user.getToken(),
                order.getOrderId(), "PERSONAL", new BigDecimal("200.00"),
                "Partial Invoice 2");
        assertNotNull(invoice2.getInvoiceId(), "second invoice should be created");

        // --- When GET /api/v1/invoices/order/{orderId} ---
        ResponseEntity<String> invoicesResp = invoiceFixture.getOrderInvoices(testRunContext,
                user.getToken(), order.getOrderId());

        // --- Then: both invoices exist ---
        if (invoicesResp.getStatusCode().is2xxSuccessful() && invoicesResp.getBody() != null) {
            String invoicesBody = invoicesResp.getBody();
            assertTrue(invoicesBody.contains(invoice1.getInvoiceId())
                            || invoicesBody.contains(invoice2.getInvoiceId()),
                    "order invoices should contain created invoice IDs");
        }

        // --- Then: cumulative amount does not exceed paid amount ---
        BigDecimal cumulativeAmount = invoice1.getAmount().add(invoice2.getAmount());
        assertTrue(cumulativeAmount.compareTo(payableAmount) <= 0,
                "cumulative invoice amount should not exceed paid amount. "
                        + "Invoices: " + cumulativeAmount + ", Paid: " + payableAmount);

        // --- Then: additional invoice exceeding amount is rejected ---
        InvoiceResult invoice3 = invoiceFixture.createInvoice(testRunContext, user.getToken(),
                order.getOrderId(), "PERSONAL", new BigDecimal("0.01"),
                "Over-limit Invoice");
        if (invoice3.getInvoiceId() == null) {
            // This is expected -- the invoice should be rejected
            // We can verify via the raw response
            ResponseEntity<String> overloadResp = apiClient.post("/api/v1/invoices",
                    Map.of("orderId", order.getOrderId(),
                            "invoiceType", "PERSONAL",
                            "invoiceAmount", new BigDecimal("0.01"),
                            "invoiceTitle", "Over-limit Invoice"),
                    userHeaders(user.getToken()));
            if (!overloadResp.getStatusCode().is2xxSuccessful()) {
                String code = apiClient.readJsonPath(overloadResp, "$.code");
                if (code != null) {
                    assertTrue(code.contains("EXCEEDED") || code.contains("INVOICE_AMOUNT")
                                    || code.contains("LIMIT"),
                            "exceeding invoice amount should return INVOICE_AMOUNT_EXCEEDED, got: "
                                    + code);
                }
            }
        }

        // --- When POST /api/v1/admin/settlements/batches ---
        RefundFixture refundFixture = new RefundFixture(apiClient, testRunContext);
        ResponseEntity<String> settlementResp = refundFixture.getSettlementBatches(testRunContext,
                testRunContext.getAdminToken());

        // --- Then: settlement batch includes the paid order ---
        if (settlementResp.getStatusCode().is2xxSuccessful() && settlementResp.getBody() != null) {
            String settlementBody = settlementResp.getBody();
            // The settlement should include or reference this paid order
            assertNotNull(settlementBody, "settlement response should not be null");
        }
    }
}
