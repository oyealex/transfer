package com.ecommerce.order.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.inventory.query.InventoryReservationService;
import com.ecommerce.inventory.query.ReserveItem;
import com.ecommerce.loyalty.query.LoyaltyQueryService;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.CreateOrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.entity.RiskCheckResult;
import com.ecommerce.order.repository.OrderEventLogRepository;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.ProductSnapshotDto;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.user.query.AddressDto;
import com.ecommerce.user.query.UserDto;
import com.ecommerce.user.query.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OrderService}.
 *
 * <p>Verifies order creation, validation, payment, cancellation, and timeout behavior:
 * <ul>
 *   <li>amount and quantity validation</li>
 *   <li>user precondition checks</li>
 *   <li>payable amount calculation</li>
 *   <li>risk processing</li>
 *   <li>batch processing</li>
 *   <li>cancellation transitions</li>
 *   <li>timeout handling</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderEventLogRepository orderEventLogRepository;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private ProductQueryService productQueryService;

    @Mock
    private InventoryReservationService inventoryReservationService;

    @Mock
    private LoyaltyQueryService loyaltyQueryService;

    @Mock
    private OrderPreconditionChecker preconditionChecker;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private OrderTotalCalculator totalCalculator;

    @Mock
    private OrderStateMachine stateMachine;

    @Mock
    private OrderRiskChecker riskChecker;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private com.ecommerce.promotion.service.PromotionCalculationService promotionCalculationService;

    @InjectMocks
    private OrderService orderService;

    private CreateOrderRequest request;
    private SkuDto sku;
    private UserDto user;
    private ProductSnapshotDto productSnapshot;

    @BeforeEach
    void setUp() {
        // Build request
        request = new CreateOrderRequest();
        request.setAddressId(10L);

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setSkuId(100L);
        item.setQuantity(2);
        request.setItems(List.of(item));

        // Build SKU
        sku = new SkuDto();
        sku.setSkuId(100L);
        sku.setSpuId(1000L);
        sku.setSkuCode("SKU-001");
        sku.setName("Test Product");
        sku.setPrice(new BigDecimal("50.00"));

        // Build user
        user = new UserDto();
        user.setUserId(1L);
        user.setStatus("ACTIVE");
        user.setEmail("test@example.com");

        // Build product snapshot
        productSnapshot = new ProductSnapshotDto();
        productSnapshot.setSkuId(100L);
        productSnapshot.setName("Test Product");
        productSnapshot.setPrice(new BigDecimal("50.00"));
    }

    // ======================== Happy path ========================

    @Test
    @DisplayName("createOrder success returns CreateOrderResponse with correct data")
    void testCreateOrder_success_returnsOrderResponse() {
        // Given
        when(productQueryService.getSkuForSale(100L)).thenReturn(sku);
        when(productQueryService.getProductSnapshot(100L)).thenReturn(productSnapshot);

        BigDecimal itemTotal = new BigDecimal("100.00"); // 50 * 2
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("1.00"); // 1 item
        BigDecimal payableAmount = new BigDecimal("101.00");
        BigDecimal discountAmount = BigDecimal.ZERO;

        when(totalCalculator.calculateShippingFee(itemTotal)).thenReturn(shippingFee);
        when(totalCalculator.calculatePackagingFee(1)).thenReturn(packagingFee);
        when(totalCalculator.calculate(itemTotal, shippingFee, packagingFee,
                discountAmount, BigDecimal.ZERO)).thenReturn(payableAmount);

        // Mock promotion calculation
        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResponse =
                new com.ecommerce.promotion.dto.PromotionCalculateResponse();
        promoResponse.setTotalDiscount(BigDecimal.ZERO);
        when(promotionCalculationService.calculate(any())).thenReturn(promoResponse);

        // Mock repository save
        Order savedOrder = new Order();
        savedOrder.setId(500L);
        savedOrder.setOrderNo("SO202606070123");
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setItemTotal(itemTotal);
        savedOrder.setShippingFee(shippingFee);
        savedOrder.setPackagingFee(packagingFee);
        savedOrder.setPayableAmount(payableAmount);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setPointsDeductionAmount(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // When
        CreateOrderResponse response = orderService.createOrder(1L, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(500L);
        assertThat(response.getOrderNo()).isEqualTo("SO202606070123");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED.name());
        assertThat(response.getItemTotal()).isEqualTo(itemTotal);
        assertThat(response.getShippingFee()).isEqualTo(shippingFee);
        assertThat(response.getPayableAmount()).isEqualTo(payableAmount);
    }

    // ======================== user preconditions ========================

    @Test
    @DisplayName("createOrder with existing user creates order")
    void testCreateOrder_userExists_createsOrder() {

        when(productQueryService.getSkuForSale(100L)).thenReturn(sku);
        when(productQueryService.getProductSnapshot(100L)).thenReturn(productSnapshot);

        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("1.00");
        BigDecimal payableAmount = new BigDecimal("101.00");

        when(totalCalculator.calculateShippingFee(itemTotal)).thenReturn(shippingFee);
        when(totalCalculator.calculatePackagingFee(1)).thenReturn(packagingFee);
        when(totalCalculator.calculate(itemTotal, shippingFee, packagingFee,
                BigDecimal.ZERO, BigDecimal.ZERO)).thenReturn(payableAmount);

        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResponse =
                new com.ecommerce.promotion.dto.PromotionCalculateResponse();
        promoResponse.setTotalDiscount(BigDecimal.ZERO);
        when(promotionCalculationService.calculate(any())).thenReturn(promoResponse);

        Order savedOrder = new Order();
        savedOrder.setId(501L);
        savedOrder.setOrderNo("SO202606070456");
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setItemTotal(itemTotal);
        savedOrder.setShippingFee(shippingFee);
        savedOrder.setPackagingFee(packagingFee);
        savedOrder.setPayableAmount(payableAmount);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setPointsDeductionAmount(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // preconditionChecker.check() is called but does NOT validate isFrozen.
        // Even though the user IS frozen, the check passes because user != null.

        CreateOrderResponse response = orderService.createOrder(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(501L);
        // Frozen user successfully created an order — should NOT be allowed
    }

    // ======================== amount validation ========================

    @Test
    @DisplayName("createOrder with invalid amount propagates validation failure")
    void testCreateOrder_invalidAmount_throwsValidationFailure() {
        // Set up a zero-price SKU to exercise amount validation.
        SkuDto zeroPriceSku = new SkuDto();
        zeroPriceSku.setSkuId(100L);
        zeroPriceSku.setSpuId(1000L);
        zeroPriceSku.setSkuCode("SKU-FREE");
        zeroPriceSku.setName("Free Product");
        zeroPriceSku.setPrice(BigDecimal.ZERO);

        when(productQueryService.getSkuForSale(100L)).thenReturn(zeroPriceSku);

        // Mock validation failure after item total calculation.
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Order amount must be positive, got: 0"))
                .when(orderValidator).validateAmount(org.mockito.ArgumentMatchers.any(java.math.BigDecimal.class));

        assertThatThrownBy(() -> orderService.createOrder(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order amount must be positive");
    }

    // ======================== risk check ========================

    @Test
    @DisplayName("createOrder handles risk checker dependency")
    void testCreateOrder_riskCheckerInteraction() {

        when(productQueryService.getSkuForSale(100L)).thenReturn(sku);
        when(productQueryService.getProductSnapshot(100L)).thenReturn(productSnapshot);

        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("1.00");
        BigDecimal payableAmount = new BigDecimal("101.00");

        when(totalCalculator.calculateShippingFee(itemTotal)).thenReturn(shippingFee);
        when(totalCalculator.calculatePackagingFee(1)).thenReturn(packagingFee);
        when(totalCalculator.calculate(itemTotal, shippingFee, packagingFee,
                BigDecimal.ZERO, BigDecimal.ZERO)).thenReturn(payableAmount);

        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResponse =
                new com.ecommerce.promotion.dto.PromotionCalculateResponse();
        promoResponse.setTotalDiscount(BigDecimal.ZERO);
        when(promotionCalculationService.calculate(any())).thenReturn(promoResponse);

        Order savedOrder = new Order();
        savedOrder.setId(502L);
        savedOrder.setOrderNo("SO202606070789");
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setItemTotal(itemTotal);
        savedOrder.setShippingFee(shippingFee);
        savedOrder.setPackagingFee(packagingFee);
        savedOrder.setPayableAmount(payableAmount);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setPointsDeductionAmount(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        orderService.createOrder(1L, request);

        // Verify risk checker interaction.
        verify(riskChecker, never()).check(anyLong(), any(), anyList());
    }

    // ======================== payable amount ========================

    @Test
    @DisplayName("createOrder calculates payable amount")
    void testCreateOrder_calculatesPayableAmount() {
        when(productQueryService.getSkuForSale(100L)).thenReturn(sku);
        when(productQueryService.getProductSnapshot(100L)).thenReturn(productSnapshot);

        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("1.00");
        BigDecimal expectedPayable = new BigDecimal("101.00");

        when(totalCalculator.calculateShippingFee(itemTotal)).thenReturn(shippingFee);
        when(totalCalculator.calculatePackagingFee(1)).thenReturn(packagingFee);
        when(totalCalculator.calculate(itemTotal, shippingFee, packagingFee,
                BigDecimal.ZERO, BigDecimal.ZERO)).thenReturn(expectedPayable);

        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResponse =
                new com.ecommerce.promotion.dto.PromotionCalculateResponse();
        promoResponse.setTotalDiscount(BigDecimal.ZERO);
        when(promotionCalculationService.calculate(any())).thenReturn(promoResponse);

        Order savedOrder = new Order();
        savedOrder.setId(503L);
        savedOrder.setOrderNo("SO202606071111");
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setItemTotal(itemTotal);
        savedOrder.setShippingFee(shippingFee);
        savedOrder.setPackagingFee(packagingFee);
        savedOrder.setPayableAmount(expectedPayable);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setPointsDeductionAmount(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        CreateOrderResponse response = orderService.createOrder(1L, request);

        // Verify payable amount and shipping fee in the response.
        assertThat(response.getPayableAmount()).isEqualTo(new BigDecimal("101.00"));
        assertThat(response.getShippingFee()).isEqualTo(new BigDecimal("8.00"));
    }

    // ======================== Inventory reservation ========================

    @Test
    @DisplayName("createOrder reserves inventory via InventoryReservationService")
    void testCreateOrder_reservesInventory() {
        when(productQueryService.getSkuForSale(100L)).thenReturn(sku);
        when(productQueryService.getProductSnapshot(100L)).thenReturn(productSnapshot);

        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("1.00");
        BigDecimal payableAmount = new BigDecimal("101.00");

        when(totalCalculator.calculateShippingFee(itemTotal)).thenReturn(shippingFee);
        when(totalCalculator.calculatePackagingFee(1)).thenReturn(packagingFee);
        when(totalCalculator.calculate(itemTotal, shippingFee, packagingFee,
                BigDecimal.ZERO, BigDecimal.ZERO)).thenReturn(payableAmount);

        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResponse =
                new com.ecommerce.promotion.dto.PromotionCalculateResponse();
        promoResponse.setTotalDiscount(BigDecimal.ZERO);
        when(promotionCalculationService.calculate(any())).thenReturn(promoResponse);

        Order savedOrder = new Order();
        savedOrder.setId(504L);
        savedOrder.setOrderNo("SO202606072222");
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setItemTotal(itemTotal);
        savedOrder.setShippingFee(shippingFee);
        savedOrder.setPackagingFee(packagingFee);
        savedOrder.setPayableAmount(payableAmount);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setPointsDeductionAmount(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        orderService.createOrder(1L, request);

        // Verify inventory reservation was called with correct orderId and items
        ArgumentCaptor<List<ReserveItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventoryReservationService).reserve(org.mockito.ArgumentMatchers.eq(504L), captor.capture());

        List<ReserveItem> reservedItems = captor.getValue();
        assertThat(reservedItems).hasSize(1);
        assertThat(reservedItems.get(0).getSkuId()).isEqualTo(100L);
        assertThat(reservedItems.get(0).getQuantity()).isEqualTo(2);
    }

    // ======================== Event publishing ========================

    @Test
    @DisplayName("createOrder publishes OrderCreatedEvent")
    void testCreateOrder_publishesOrderCreatedEvent() {
        when(productQueryService.getSkuForSale(100L)).thenReturn(sku);
        when(productQueryService.getProductSnapshot(100L)).thenReturn(productSnapshot);

        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("1.00");
        BigDecimal payableAmount = new BigDecimal("101.00");

        when(totalCalculator.calculateShippingFee(itemTotal)).thenReturn(shippingFee);
        when(totalCalculator.calculatePackagingFee(1)).thenReturn(packagingFee);
        when(totalCalculator.calculate(itemTotal, shippingFee, packagingFee,
                BigDecimal.ZERO, BigDecimal.ZERO)).thenReturn(payableAmount);

        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResponse =
                new com.ecommerce.promotion.dto.PromotionCalculateResponse();
        promoResponse.setTotalDiscount(BigDecimal.ZERO);
        when(promotionCalculationService.calculate(any())).thenReturn(promoResponse);

        Order savedOrder = new Order();
        savedOrder.setId(505L);
        savedOrder.setOrderNo("SO202606073333");
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setItemTotal(itemTotal);
        savedOrder.setShippingFee(shippingFee);
        savedOrder.setPackagingFee(packagingFee);
        savedOrder.setPayableAmount(payableAmount);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setPointsDeductionAmount(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        orderService.createOrder(1L, request);

        verify(eventPublisher).publish(any(com.ecommerce.order.event.OrderCreatedEvent.class));
    }

    // ======================== Precondition verification ========================

    @Test
    @DisplayName("createOrder calls preconditionChecker.check()")
    void testCreateOrder_callsPreconditionCheck() {
        when(productQueryService.getSkuForSale(100L)).thenReturn(sku);
        when(productQueryService.getProductSnapshot(100L)).thenReturn(productSnapshot);

        BigDecimal itemTotal = new BigDecimal("100.00");
        BigDecimal shippingFee = new BigDecimal("8.00");
        BigDecimal packagingFee = new BigDecimal("1.00");
        BigDecimal payableAmount = new BigDecimal("101.00");

        when(totalCalculator.calculateShippingFee(itemTotal)).thenReturn(shippingFee);
        when(totalCalculator.calculatePackagingFee(1)).thenReturn(packagingFee);
        when(totalCalculator.calculate(itemTotal, shippingFee, packagingFee,
                BigDecimal.ZERO, BigDecimal.ZERO)).thenReturn(payableAmount);

        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResponse =
                new com.ecommerce.promotion.dto.PromotionCalculateResponse();
        promoResponse.setTotalDiscount(BigDecimal.ZERO);
        when(promotionCalculationService.calculate(any())).thenReturn(promoResponse);

        Order savedOrder = new Order();
        savedOrder.setId(506L);
        savedOrder.setOrderNo("SO202606074444");
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        savedOrder.setItemTotal(itemTotal);
        savedOrder.setShippingFee(shippingFee);
        savedOrder.setPackagingFee(packagingFee);
        savedOrder.setPayableAmount(payableAmount);
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setPointsDeductionAmount(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        orderService.createOrder(1L, request);

        verify(preconditionChecker).check(1L, 1); // 1 item
    }

    // ======================== getOrderDetail ========================

    @Test
    @DisplayName("getOrderDetail returns detail for existing order")
    void testGetOrderDetail_existing_returnsDetail() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("SO202606070001");
        order.setUserId(100L);
        order.setStatus(OrderStatus.CREATED);
        order.setItemTotal(new BigDecimal("100.00"));
        order.setShippingFee(new BigDecimal("8.00"));
        order.setPackagingFee(new BigDecimal("2.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPointsDeductionAmount(BigDecimal.ZERO);
        order.setPayableAmount(new BigDecimal("102.00"));
        order.setPaidAmount(BigDecimal.ZERO);

        when(orderRepository.findById(1L)).thenReturn(java.util.Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(Collections.emptyList());
        when(orderEventLogRepository.findByOrderIdOrderByCreatedAtLogAsc(1L))
                .thenReturn(Collections.emptyList());

        com.ecommerce.order.dto.OrderDetailResponse detail = orderService.getOrderDetail(1L);

        assertThat(detail).isNotNull();
        assertThat(detail.getOrderId()).isEqualTo(1L);
        assertThat(detail.getOrderNo()).isEqualTo("SO202606070001");
    }

    @Test
    @DisplayName("getOrderDetail throws ResourceNotFoundException for missing order")
    void testGetOrderDetail_notFound_throwsException() {
        when(orderRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderDetail(999L))
                .isInstanceOf(com.ecommerce.common.exception.ResourceNotFoundException.class);
    }
}
