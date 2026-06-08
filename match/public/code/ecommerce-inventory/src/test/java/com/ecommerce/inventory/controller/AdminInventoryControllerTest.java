package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.InboundRequest;
import com.ecommerce.inventory.dto.StockWarningResponse;
import com.ecommerce.inventory.dto.WarehouseCreateRequest;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.StockAdjustment;
import com.ecommerce.inventory.entity.StockWarningRule;
import com.ecommerce.inventory.entity.Warehouse;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.inventory.service.StockAdjustmentService;
import com.ecommerce.inventory.service.StockWarningService;
import com.ecommerce.inventory.service.WarehouseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminInventoryController")
@WebMvcTest(AdminInventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WarehouseService warehouseService;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private StockWarningService stockWarningService;

    @MockBean
    private StockAdjustmentService stockAdjustmentService;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- warehouse tests ----

    @Test
    @DisplayName("POST /api/v1/admin/warehouses creates warehouse and returns 201")
    void testCreateWarehouse_returnsCreated() throws Exception {
        WarehouseCreateRequest request = new WarehouseCreateRequest();
        request.setName("Main Warehouse");
        request.setProvince("Guangdong");
        request.setCity("Shenzhen");
        request.setPriority(1);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Main Warehouse");
        warehouse.setProvince("Guangdong");
        warehouse.setCity("Shenzhen");
        warehouse.setStatus("ACTIVE");
        warehouse.setPriority(1);

        when(warehouseService.create(any(WarehouseCreateRequest.class))).thenReturn(warehouse);

        mockMvc.perform(post("/api/v1/admin/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Main Warehouse"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ---- inbound tests ----

    @Test
    @DisplayName("POST /api/v1/admin/inventory/inbound creates inbound and returns 201")
    void testInbound_returnsCreated() throws Exception {
        InboundRequest request = new InboundRequest();
        request.setWarehouseId(1L);
        request.setSkuId(100L);
        request.setQuantity(50);

        InventoryStock stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(50);
        stock.setReservedStock(0);

        when(inventoryService.inbound(any(InboundRequest.class))).thenReturn(stock);

        mockMvc.perform(post("/api/v1/admin/inventory/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseId").value(1))
                .andExpect(jsonPath("$.skuId").value(100))
                .andExpect(jsonPath("$.onHandStock").value(50));
    }

    // ---- outbound tests ----

    @Test
    @DisplayName("POST /api/v1/admin/inventory/outbound creates outbound and returns 201")
    void testOutbound_returnsCreated() throws Exception {
        InventoryStock stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(70);
        stock.setReservedStock(0);

        when(inventoryService.outbound(eq(1L), eq(100L), eq(30), isNull())).thenReturn(stock);

        mockMvc.perform(post("/api/v1/admin/inventory/outbound")
                        .param("warehouseId", "1")
                        .param("skuId", "100")
                        .param("quantity", "30"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseId").value(1))
                .andExpect(jsonPath("$.skuId").value(100))
                .andExpect(jsonPath("$.onHandStock").value(70));

        verify(inventoryService).outbound(1L, 100L, 30, null);
    }

    @Test
    @DisplayName("POST /api/v1/admin/inventory/outbound includes optional orderId")
    void testOutbound_withOrderId() throws Exception {
        InventoryStock stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(80);

        when(inventoryService.outbound(eq(1L), eq(100L), eq(20), eq(42L))).thenReturn(stock);

        mockMvc.perform(post("/api/v1/admin/inventory/outbound")
                        .param("warehouseId", "1")
                        .param("skuId", "100")
                        .param("quantity", "20")
                        .param("orderId", "42"))
                .andExpect(status().isCreated());

        verify(inventoryService).outbound(1L, 100L, 20, 42L);
    }

    // ---- adjustment tests ----

    @Test
    @DisplayName("POST /api/v1/admin/inventory/adjustments creates adjustment and returns 201")
    void testCreateAdjustment_returnsCreated() throws Exception {
        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setId(1L);
        adjustment.setWarehouseId(1L);
        adjustment.setSkuId(100L);
        adjustment.setBeforeQty(100);
        adjustment.setAfterQty(80);
        adjustment.setReason("Physical count");

        when(stockAdjustmentService.create(eq(1L), eq(100L), eq(80), eq("Physical count")))
                .thenReturn(adjustment);

        mockMvc.perform(post("/api/v1/admin/inventory/adjustments")
                        .param("warehouseId", "1")
                        .param("skuId", "100")
                        .param("afterQty", "80")
                        .param("reason", "Physical count"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.beforeQty").value(100))
                .andExpect(jsonPath("$.afterQty").value(80))
                .andExpect(jsonPath("$.reason").value("Physical count"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/inventory/adjustments returns adjustments for warehouse")
    void testListAdjustments_returnsList() throws Exception {
        StockAdjustment adj1 = new StockAdjustment();
        adj1.setId(1L);
        adj1.setWarehouseId(1L);
        adj1.setSkuId(100L);
        adj1.setBeforeQty(100);
        adj1.setAfterQty(80);
        adj1.setReason("Count 1");

        StockAdjustment adj2 = new StockAdjustment();
        adj2.setId(2L);
        adj2.setWarehouseId(1L);
        adj2.setSkuId(200L);
        adj2.setBeforeQty(50);
        adj2.setAfterQty(60);
        adj2.setReason("Count 2");

        when(stockAdjustmentService.list(1L)).thenReturn(List.of(adj1, adj2));

        mockMvc.perform(get("/api/v1/admin/inventory/adjustments")
                        .param("warehouseId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].reason").value("Count 1"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].reason").value("Count 2"));
    }

    // ---- warnings tests ----

    @Test
    @DisplayName("GET /api/v1/admin/inventory/warnings returns stock warnings")
    void testGetWarnings_returnsWarnings() throws Exception {
        StockWarningResponse warning = new StockWarningResponse();
        warning.setSkuId(100L);
        warning.setWarehouseId(1L);
        warning.setOnHandStock(5);
        warning.setSafetyStock(10);
        warning.setWarningThreshold(20);
        warning.setMessage("SKU 100 in warehouse 1 is below warning threshold: 5 <= 20");

        when(stockWarningService.getWarnings()).thenReturn(List.of(warning));

        mockMvc.perform(get("/api/v1/admin/inventory/warnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuId").value(100))
                .andExpect(jsonPath("$[0].warehouseId").value(1))
                .andExpect(jsonPath("$[0].onHandStock").value(5))
                .andExpect(jsonPath("$[0].warningThreshold").value(20));
    }

    @Test
    @DisplayName("POST /api/v1/admin/inventory/warnings/rule sets a warning rule and returns 200")
    void testSetWarningRule_returnsOk() throws Exception {
        StockWarningRule rule = new StockWarningRule();
        rule.setId(1L);
        rule.setSkuId(100L);
        rule.setWarehouseId(1L);
        rule.setWarningThreshold(15);
        rule.setEnabled(true);

        when(stockWarningService.setWarningRule(eq(100L), eq(1L), eq(15))).thenReturn(rule);

        mockMvc.perform(post("/api/v1/admin/inventory/warnings/rule")
                        .param("skuId", "100")
                        .param("warehouseId", "1")
                        .param("warningThreshold", "15"))
                .andExpect(status().isOk());

        verify(stockWarningService).setWarningRule(100L, 1L, 15);
    }

    @Test
    @DisplayName("POST /api/v1/admin/inventory/warnings/rule with null warehouseId sets global rule")
    void testSetWarningRule_nullWarehouseId() throws Exception {
        mockMvc.perform(post("/api/v1/admin/inventory/warnings/rule")
                        .param("skuId", "100")
                        .param("warningThreshold", "10"))
                .andExpect(status().isOk());

        verify(stockWarningService).setWarningRule(eq(100L), isNull(), eq(10));
    }
}
