package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.RefundApplyRequest;
import com.ecommerce.payment.dto.RefundResponse;
import com.ecommerce.payment.entity.RefundStatus;
import com.ecommerce.payment.service.RefundService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RefundController.class)
@AutoConfigureMockMvc(addFilters = false)
class RefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RefundService refundService;

    // ---- POST /api/v1/refunds/apply -> 201 ----

    @Test
    @WithMockUser(username = "100", roles = {"USER"})
    @DisplayName("POST /api/v1/refunds/apply should return 201")
    void postApplyRefund_shouldReturn201() throws Exception {
        RefundApplyRequest request = new RefundApplyRequest(1L, "PAY123", "Changed mind");

        RefundResponse response = new RefundResponse();
        response.setId(1L);
        response.setRefundNo("RF001");
        response.setPaymentNo("PAY123");
        response.setOrderId(1L);
        response.setUserId(100L);
        response.setRefundAmount(new BigDecimal("97.00"));
        response.setReason("Changed mind");
        response.setStatus(RefundStatus.PENDING_REVIEW);

        when(refundService.applyRefund(anyLong(), any(RefundApplyRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/refunds/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.refundNo").value("RF001"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        verify(refundService).applyRefund(anyLong(), any(RefundApplyRequest.class));
    }

    // ---- GET /api/v1/refunds/{refundId} -> 200 ----

    @Test
    @WithMockUser(username = "100", roles = {"USER"})
    @DisplayName("GET /api/v1/refunds/{refundId} should return 200")
    void getRefund_shouldReturn200() throws Exception {
        Long refundId = 1L;

        RefundResponse response = new RefundResponse();
        response.setId(refundId);
        response.setRefundNo("RF001");
        response.setPaymentNo("PAY123");
        response.setOrderId(1L);
        response.setUserId(100L);
        response.setRefundAmount(new BigDecimal("97.00"));
        response.setStatus(RefundStatus.COMPLETED);
        response.setCompletedAt(java.time.LocalDateTime.now());

        when(refundService.getRefund(refundId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/refunds/{refundId}", refundId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.refundNo").value("RF001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(refundService).getRefund(refundId);
    }
}
