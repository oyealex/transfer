package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.InventoryCheckRequest;
import com.ecommerce.inventory.dto.InventoryCheckResponse;
import com.ecommerce.inventory.dto.StockSummaryResponse;
import com.ecommerce.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("InventoryController")
@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/inventory/sku/{skuId} returns stock summary")
    void testGetSkuStock_returnsSummary() throws Exception {
        StockSummaryResponse response = new StockSummaryResponse();
        response.setSkuId(100L);
        response.setSkuName("Test Product");
        response.setOnHandStock(200);
        response.setReservedStock(20);
        response.setAvailableStock(180);

        when(inventoryService.getStockSummaryResponse(100L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/inventory/sku/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuId").value(100))
                .andExpect(jsonPath("$.skuName").value("Test Product"))
                .andExpect(jsonPath("$.onHandStock").value(200))
                .andExpect(jsonPath("$.reservedStock").value(20))
                .andExpect(jsonPath("$.availableStock").value(180));

        verify(inventoryService).getStockSummaryResponse(100L);
    }

    @Test
    @DisplayName("GET /api/v1/inventory/sku/{skuId} returns 200 with skuName null when product not found")
    void testGetSkuStock_nullSkuName() throws Exception {
        StockSummaryResponse response = new StockSummaryResponse();
        response.setSkuId(999L);
        response.setSkuName(null);
        response.setOnHandStock(0);
        response.setReservedStock(0);
        response.setAvailableStock(0);

        when(inventoryService.getStockSummaryResponse(999L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/inventory/sku/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuName").doesNotExist())
                .andExpect(jsonPath("$.availableStock").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/check returns availability check with available=true when stock is sufficient")
    void testCheckAvailability_available() throws Exception {
        InventoryCheckRequest request = new InventoryCheckRequest();
        request.setSkuId(100L);
        request.setQuantity(50);

        InventoryCheckResponse response = new InventoryCheckResponse(100L, true, 200);

        when(inventoryService.checkAndReport(anyLong(), anyInt())).thenReturn(response);

        mockMvc.perform(post("/api/v1/inventory/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuId").value(100))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.availableStock").value(200));

        verify(inventoryService).checkAndReport(100L, 50);
    }

    @Test
    @DisplayName("POST /api/v1/inventory/check returns available=false when stock is insufficient")
    void testCheckAvailability_unavailable() throws Exception {
        InventoryCheckRequest request = new InventoryCheckRequest();
        request.setSkuId(200L);
        request.setQuantity(500);

        InventoryCheckResponse response = new InventoryCheckResponse(200L, false, 10);

        when(inventoryService.checkAndReport(anyLong(), anyInt())).thenReturn(response);

        mockMvc.perform(post("/api/v1/inventory/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuId").value(200))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.availableStock").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/check returns 400 when request body is invalid (missing skuId)")
    void testCheckAvailability_missingSkuId_returnsBadRequest() throws Exception {
        String invalidJson = "{\"quantity\": 50}";

        mockMvc.perform(post("/api/v1/inventory/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
