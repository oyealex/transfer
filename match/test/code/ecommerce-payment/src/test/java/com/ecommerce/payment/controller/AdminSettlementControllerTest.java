package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.SettlementBatchResponse;
import com.ecommerce.payment.entity.SettlementStatus;
import com.ecommerce.payment.service.SettlementBatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminSettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettlementBatchService settlementBatchService;

    // ---- POST /api/v1/admin/settlements/batches -> 201 ----

    @Test
    @WithMockUser(username = "999", roles = {"ADMIN"})
    @DisplayName("POST /api/v1/admin/settlements/batches should return 201")
    void postGenerateBatch_shouldReturn201() throws Exception {
        // Settlement batch includes all payment records regardless of status
        SettlementBatchResponse response = new SettlementBatchResponse();
        response.setId(1L);
        response.setBatchNo("BAT20260601ABC");
        response.setBatchDate(LocalDate.of(2026, 6, 1));
        response.setTotalPaymentAmount(new BigDecimal("600.00"));
        response.setTotalRefundAmount(BigDecimal.ZERO);
        response.setTotalInvoiceAmount(BigDecimal.ZERO);
        response.setOrderCount(3); // Includes all order statuses, not just SUCCESS
        response.setStatus(SettlementStatus.GENERATED);
        response.setGeneratedAt(java.time.LocalDateTime.now());

        when(settlementBatchService.generateBatch(any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/settlements/batches")
                        .param("batchDate", "2026-06-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchNo").value("BAT20260601ABC"))
                .andExpect(jsonPath("$.orderCount").value(3))
                .andExpect(jsonPath("$.status").value("GENERATED"));

        verify(settlementBatchService).generateBatch(any(LocalDate.class));
    }

    @Test
    @WithMockUser(username = "999", roles = {"ADMIN"})
    @DisplayName("POST /api/v1/admin/settlements/batches without date uses today")
    void postGenerateBatch_withoutDate_usesToday() throws Exception {
        SettlementBatchResponse response = new SettlementBatchResponse();
        response.setId(1L);
        response.setBatchNo("BAT20260607DEF");
        response.setBatchDate(LocalDate.now());
        response.setTotalPaymentAmount(new BigDecimal("1000.00"));
        response.setTotalRefundAmount(BigDecimal.ZERO);
        response.setTotalInvoiceAmount(BigDecimal.ZERO);
        response.setOrderCount(5);
        response.setStatus(SettlementStatus.GENERATED);

        when(settlementBatchService.generateBatch(any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/settlements/batches"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchNo").value("BAT20260607DEF"))
                .andExpect(jsonPath("$.orderCount").value(5));

        verify(settlementBatchService).generateBatch(any(LocalDate.class));
    }
}
