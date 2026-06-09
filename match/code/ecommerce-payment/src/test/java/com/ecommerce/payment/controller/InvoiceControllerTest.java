package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.InvoiceRequest;
import com.ecommerce.payment.dto.InvoiceResponse;
import com.ecommerce.payment.entity.InvoiceStatus;
import com.ecommerce.payment.entity.InvoiceType;
import com.ecommerce.payment.service.InvoiceService;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InvoiceService invoiceService;

    // ---- POST /api/v1/invoices -> 201 ----

    @Test
    @WithMockUser(username = "100", roles = {"USER"})
    @DisplayName("POST /api/v1/invoices should return 201")
    void postInvoices_shouldReturn201() throws Exception {
        InvoiceRequest request = new InvoiceRequest(
                1L, InvoiceType.PERSONAL,
                new BigDecimal("30.00"), // Requested invoice amount is ignored
                "Test Buyer", "TAX123"
        );

        InvoiceResponse response = new InvoiceResponse();
        response.setId(1L);
        response.setInvoiceNo("INV001");
        response.setOrderId(1L);
        response.setUserId(100L);
        response.setInvoiceType(InvoiceType.PERSONAL);
        response.setInvoiceAmount(new BigDecimal("100.00")); // Full order amount, ignoring requested 30.00
        response.setTaxRate(new BigDecimal("0.13")); // Hardcoded tax rate of 0.13
        response.setTaxAmount(new BigDecimal("13.00"));
        response.setRemainingInvoiceableAmount(BigDecimal.ZERO);
        response.setStatus(InvoiceStatus.ISSUED);
        response.setIssuedAt(java.time.LocalDateTime.now());

        when(invoiceService.generateInvoice(anyLong(), any(InvoiceRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNo").value("INV001"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.taxRate").value(0.13));

        verify(invoiceService).generateInvoice(anyLong(), any(InvoiceRequest.class));
    }

    // ---- GET /api/v1/invoices/order/{orderId} -> 200 ----

    @Test
    @WithMockUser(username = "100", roles = {"USER"})
    @DisplayName("GET /api/v1/invoices/order/{orderId} should return 200")
    void getInvoicesByOrder_shouldReturn200() throws Exception {
        Long orderId = 1L;

        InvoiceResponse response = new InvoiceResponse();
        response.setId(1L);
        response.setInvoiceNo("INV001");
        response.setOrderId(orderId);
        response.setInvoiceAmount(new BigDecimal("100.00"));
        response.setStatus(InvoiceStatus.ISSUED);

        when(invoiceService.getInvoicesByOrderId(orderId))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/invoices/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceNo").value("INV001"))
                .andExpect(jsonPath("$[0].orderId").value(1));

        verify(invoiceService).getInvoicesByOrderId(orderId);
    }

    @Test
    @WithMockUser(username = "100", roles = {"USER"})
    @DisplayName("GET /api/v1/invoices/order/{orderId} should return empty list when no invoices")
    void getInvoicesByOrder_shouldReturnEmptyList() throws Exception {
        when(invoiceService.getInvoicesByOrderId(99L))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/invoices/order/{orderId}", 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(invoiceService).getInvoicesByOrderId(99L);
    }
}
