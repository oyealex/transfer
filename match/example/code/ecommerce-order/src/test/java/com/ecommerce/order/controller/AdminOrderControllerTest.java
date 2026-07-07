package com.ecommerce.order.controller;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.order.dto.AdminCancelReviewRequest;
import com.ecommerce.order.dto.CancelOrderResponse;
import com.ecommerce.order.dto.SalesStatisticsRequest;
import com.ecommerce.order.dto.SalesStatisticsResponse;
import com.ecommerce.order.service.OrderCancelService;
import com.ecommerce.order.service.OrderTimeoutService;
import com.ecommerce.order.service.SalesStatisticsService;
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
import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link AdminOrderController} using standalone MockMvc setup.
 * All endpoints require ADMIN role.
 */
@DisplayName("AdminOrderController")
class AdminOrderControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private OrderCancelService orderCancelService;
    private SalesStatisticsService salesStatisticsService;
    private OrderTimeoutService orderTimeoutService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);
        orderCancelService = mock(OrderCancelService.class);
        salesStatisticsService = mock(SalesStatisticsService.class);
        orderTimeoutService = mock(OrderTimeoutService.class);

        AdminOrderController controller = new AdminOrderController(
                orderCancelService, salesStatisticsService, orderTimeoutService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(jacksonConverter)
                .build();

        // Set up authentication so getCurrentUserId() works
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("200");
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ======================== cancel-review ========================

    @Test
    @DisplayName("reviewCancel approves cancellation: returns 200 OK")
    void testReviewCancel_approve_returns200() throws Exception {
        CancelOrderResponse response = new CancelOrderResponse(1L, "CANCELLED",
                "Cancellation approved");
        when(orderCancelService.reviewCancel(eq(1L), eq(true), anyString(), eq(200L)))
                .thenReturn(response);

        AdminCancelReviewRequest request = new AdminCancelReviewRequest();
        request.setDecision("APPROVE");
        request.setComment("Looks good");

        mockMvc.perform(post("/api/v1/admin/orders/1/cancel-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.message").value("Cancellation approved"));
    }

    @Test
    @DisplayName("reviewCancel rejects cancellation: returns 200 OK with PAID status")
    void testReviewCancel_reject_returns200() throws Exception {
        CancelOrderResponse response = new CancelOrderResponse(1L, "PAID",
                "Cancellation rejected");
        when(orderCancelService.reviewCancel(eq(1L), eq(false), anyString(), eq(200L)))
                .thenReturn(response);

        AdminCancelReviewRequest request = new AdminCancelReviewRequest();
        request.setDecision("REJECT");
        request.setComment("Not authorized");

        mockMvc.perform(post("/api/v1/admin/orders/1/cancel-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("reviewCancel with invalid decision returns 400 Bad Request")
    void testReviewCancel_invalidDecision_returns400() throws Exception {
        // Simulate a business exception thrown by the service for invalid decision
        when(orderCancelService.reviewCancel(eq(1L), anyBoolean(), anyString(), eq(200L)))
                .thenThrow(new BusinessException("INVALID_DECISION", "Decision must be APPROVE or REJECT"));

        AdminCancelReviewRequest request = new AdminCancelReviewRequest();
        request.setDecision("INVALID");
        request.setComment("Bad decision");

        mockMvc.perform(post("/api/v1/admin/orders/1/cancel-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ======================== sales statistics ========================

    @Test
    @DisplayName("getSalesStatistics returns 200 OK with sales data")
    void testGetSalesStatistics_returns200() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 7);

        SalesStatisticsResponse statsResponse = new SalesStatisticsResponse();
        statsResponse.setStartDate(startDate);
        statsResponse.setEndDate(endDate);
        statsResponse.setTotalOrders(10);
        statsResponse.setTotalAmount(new BigDecimal("5000.00"));
        statsResponse.setTotalDiscount(new BigDecimal("500.00"));
        statsResponse.setBreakdown(Collections.emptyList());

        when(salesStatisticsService.getSalesStatistics(any(SalesStatisticsRequest.class)))
                .thenReturn(statsResponse);

        mockMvc.perform(get("/api/v1/admin/orders/statistics/sales")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-06-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-06-01"))
                .andExpect(jsonPath("$.endDate").value("2026-06-07"))
                .andExpect(jsonPath("$.totalOrders").value(10))
                .andExpect(jsonPath("$.totalAmount").value(5000.00))
                .andExpect(jsonPath("$.totalDiscount").value(500.00));
    }

    @Test
    @DisplayName("getSalesStatistics with invalid date range returns 400")
    void testGetSalesStatistics_invalidDates_returns400() throws Exception {
        when(salesStatisticsService.getSalesStatistics(any(SalesStatisticsRequest.class)))
                .thenThrow(new BusinessException("INVALID_DATE_RANGE", "Start date and end date are required"));

        mockMvc.perform(get("/api/v1/admin/orders/statistics/sales")
                        .param("startDate", "2026-06-07")
                        .param("endDate", "2026-06-01"))
                .andExpect(status().isBadRequest());
    }
}
