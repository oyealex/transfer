package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.dto.PayResponse;
import com.ecommerce.payment.dto.PaymentCallbackRequest;
import com.ecommerce.payment.entity.PaymentMethod;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.service.PaymentCallbackService;
import com.ecommerce.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private PaymentCallbackService paymentCallbackService;

    // ---- POST /api/v1/payment/pay -> 201 ----

    @Test
    @WithMockUser(username = "100", roles = {"USER"})
    @DisplayName("POST /api/v1/payment/pay should return 201")
    void postPay_shouldReturn201() throws Exception {
        PayRequest request = new PayRequest(1L, new BigDecimal("99.00"),
                PaymentMethod.ALIPAY, "CLIENT001");

        PayResponse response = new PayResponse("PAY123", 1L,
                PaymentStatus.PENDING, new BigDecimal("99.00"),
                java.time.LocalDateTime.now());

        when(paymentService.pay(any(PayRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/payment/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentNo").value("PAY123"))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paidAmount").value(99.00));

        verify(paymentService).pay(any(PayRequest.class));
    }

    // ---- GET /api/v1/payment/{paymentNo} -> 200 ----

    @Test
    @WithMockUser(username = "100", roles = {"USER"})
    @DisplayName("GET /api/v1/payment/{paymentNo} should return 200")
    void getPayment_shouldReturn200() throws Exception {
        String paymentNo = "PAY123";

        PayResponse response = new PayResponse(paymentNo, 1L,
                PaymentStatus.SUCCESS, new BigDecimal("99.00"),
                java.time.LocalDateTime.now());

        when(paymentService.getPayment(paymentNo)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payment/{paymentNo}", paymentNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNo").value(paymentNo))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(paymentService).getPayment(paymentNo);
    }

    // ---- POST /api/v1/payment/callback -> 200 ----

    @Test
    @DisplayName("POST /api/v1/payment/callback should return 200")
    void postCallback_shouldReturn200() throws Exception {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY123", 1L, "SUCCESS",
                new BigDecimal("99.00"), "seq-001",
                "test_signature" // Signature is not validated
        );

        mockMvc.perform(post("/api/v1/payment/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("OK"));

        verify(paymentCallbackService).processCallback(any(PaymentCallbackRequest.class));
    }
}
