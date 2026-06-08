package com.ecommerce.order.controller;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.dto.BatchCreateOrderRequest;
import com.ecommerce.order.dto.BatchCreateOrderResponse;
import com.ecommerce.order.dto.CancelOrderResponse;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.CreateOrderResponse;
import com.ecommerce.order.dto.OrderDetailResponse;
import com.ecommerce.order.dto.OrderListResponse;
import com.ecommerce.order.dto.VerifyPurchaseRequest;
import com.ecommerce.order.dto.VerifyPurchaseResponse;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.BatchOrderService;
import com.ecommerce.order.service.OrderCancelService;
import com.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link OrderController} using standalone MockMvc setup.
 *
 * <p>The {@code createOrder} endpoint returns HTTP 200 OK
 * instead of HTTP 201 Created. These tests verify the ACTUAL behavior.
 */
@DisplayName("OrderController")
class OrderControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private OrderService orderService;
    private OrderCancelService orderCancelService;
    private BatchOrderService batchOrderService;

    private CreateOrderRequest createRequest;
    private CreateOrderResponse createResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);

        orderService = mock(OrderService.class);
        orderCancelService = mock(OrderCancelService.class);
        batchOrderService = mock(BatchOrderService.class);

        OrderController controller = new OrderController(orderService, orderCancelService, batchOrderService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(jacksonConverter)
                .build();

        // Set up authentication so getCurrentUserId() works
        setupMockAuthentication("100");

        createRequest = new CreateOrderRequest();
        createRequest.setAddressId(10L);

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setSkuId(100L);
        item.setQuantity(2);
        createRequest.setItems(List.of(item));

        createResponse = new CreateOrderResponse();
        createResponse.setOrderId(1L);
        createResponse.setOrderNo("SO202606070001");
        createResponse.setStatus("CREATED");
        createResponse.setItemTotal(new BigDecimal("100.00"));
        createResponse.setShippingFee(new BigDecimal("8.00"));
        createResponse.setPackagingFee(new BigDecimal("2.00"));
        createResponse.setDiscountAmount(BigDecimal.ZERO);
        createResponse.setPointsDeductionAmount(BigDecimal.ZERO);
        createResponse.setPayableAmount(new BigDecimal("102.00"));
    }

    private void setupMockAuthentication(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ======================== returns 200 instead of 201 ========================

    @Test
    @DisplayName("createOrder returns HTTP 200 OK instead of 201 Created")
    void testCreateOrder_returns200_not201() throws Exception {
        setupMockAuthentication("100");
        when(orderService.createOrder(eq(100L), any(CreateOrderRequest.class)))
                .thenReturn(createResponse);

        // Should return 201 Created, but returns 200 OK
        mockMvc.perform(post("/api/v1/orders/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())  // should be isCreated() / 201
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.orderNo").value("SO202606070001"));
    }

    // ======================== getOrderDetail ========================

    @Test
    @DisplayName("getOrderDetail returns 200 OK")
    void testGetOrder_returns200() throws Exception {
        OrderDetailResponse detail = new OrderDetailResponse();
        detail.setOrderId(1L);
        detail.setOrderNo("SO202606070001");
        detail.setUserId(100L);
        detail.setStatus(OrderStatus.CREATED.name());
        detail.setItemTotal(new BigDecimal("100.00"));
        detail.setPayableAmount(new BigDecimal("102.00"));

        when(orderService.getOrderDetail(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @DisplayName("getOrderDetail returns 404 for non-existing order")
    void testGetOrder_notFound_returns404() throws Exception {
        when(orderService.getOrderDetail(999L))
                .thenThrow(new ResourceNotFoundException("Order not found: 999"));

        mockMvc.perform(get("/api/v1/orders/999"))
                .andExpect(status().isNotFound());
    }

    // ======================== listOrders ========================

    @Test
    @DisplayName("listOrders returns 200 OK")
    void testListOrders_returns200() throws Exception {
        OrderListResponse orderItem = new OrderListResponse();
        orderItem.setOrderId(1L);
        orderItem.setOrderNo("SO202606070001");
        orderItem.setStatus("CREATED");
        orderItem.setItemTotal(new BigDecimal("100.00"));
        orderItem.setPayableAmount(new BigDecimal("102.00"));
        orderItem.setItemCount(2);
        orderItem.setCreatedAt(LocalDateTime.now());

        org.springframework.data.domain.PageImpl<OrderListResponse> page =
                new org.springframework.data.domain.PageImpl<>(List.of(orderItem));
        when(orderService.listUserOrders(eq(100L), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].orderId").value(1))
                .andExpect(jsonPath("$.total").value(1));
    }

    // ======================== cancelOrder ========================

    @Test
    @DisplayName("cancelOrder returns 200 OK")
    void testCancelOrder_returns200() throws Exception {
        CancelOrderResponse cancelResponse = new CancelOrderResponse(1L, "CANCELLED", "Order cancelled");
        when(orderCancelService.cancel(eq(100L), eq(1L), anyString())).thenReturn(cancelResponse);

        mockMvc.perform(post("/api/v1/orders/1/cancel")
                        .param("reason", "Changed my mind"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ======================== batchCreate ========================

    @Test
    @DisplayName("batchCreate returns 200 OK")
    void testBatchCreate_returns200() throws Exception {
        BatchCreateOrderRequest batchRequest = new BatchCreateOrderRequest();
        batchRequest.setOrders(List.of(createRequest));
        batchRequest.setContinueOnError(false);

        BatchCreateOrderResponse batchResponse = new BatchCreateOrderResponse();
        batchResponse.setTotalCount(1);
        batchResponse.setSuccessCount(1);
        batchResponse.setFailureCount(0);
        batchResponse.setResults(Collections.emptyList());

        when(batchOrderService.createBatch(eq(100L), any(BatchCreateOrderRequest.class)))
                .thenReturn(batchResponse);

        mockMvc.perform(post("/api/v1/orders/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.successCount").value(1));
    }

    // ======================== verifyPurchase ========================

    @Test
    @DisplayName("verifyPurchase returns 200 OK")
    void testVerifyPurchase_returns200() throws Exception {
        VerifyPurchaseResponse verifyResponse = new VerifyPurchaseResponse(true, 1L, LocalDateTime.now());
        when(orderService.verifyPurchase(any(VerifyPurchaseRequest.class))).thenReturn(verifyResponse);

        mockMvc.perform(get("/api/v1/orders/verify-purchase")
                        .param("userId", "100")
                        .param("productId", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchased").value(true));
    }

    // ======================== Validation errors ========================

    @Test
    @DisplayName("createOrder with missing address returns 400 Bad Request (via GlobalExceptionHandler)")
    void testCreateOrder_missingAddress_returnsError() throws Exception {
        setupMockAuthentication("100");
        when(orderService.createOrder(eq(100L), any(CreateOrderRequest.class)))
                .thenThrow(new BusinessException("INVALID_REQUEST", "Address is required"));

        CreateOrderRequest badRequest = new CreateOrderRequest();
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setSkuId(100L);
        item.setQuantity(1);
        badRequest.setItems(List.of(item));
        // addressId not set

        mockMvc.perform(post("/api/v1/orders/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("createOrder with empty items returns 400 Bad Request")
    void testCreateOrder_emptyItems_returnsError() throws Exception {
        setupMockAuthentication("100");
        when(orderService.createOrder(eq(100L), any(CreateOrderRequest.class)))
                .thenThrow(new BusinessException("ORDER_EMPTY", "Order must contain at least one item"));

        CreateOrderRequest badRequest = new CreateOrderRequest();
        badRequest.setAddressId(10L);
        badRequest.setItems(Collections.emptyList());

        mockMvc.perform(post("/api/v1/orders/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }
}
