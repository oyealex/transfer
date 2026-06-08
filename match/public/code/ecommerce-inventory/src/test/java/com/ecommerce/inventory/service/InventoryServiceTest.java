package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.dto.InboundRequest;
import com.ecommerce.inventory.dto.InventoryCheckResponse;
import com.ecommerce.inventory.dto.StockSummaryResponse;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.repository.InboundOrderRepository;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.OutboundOrderRepository;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.product.query.StockSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InventoryService")
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryStockRepository stockRepo;

    @Mock
    private InboundOrderRepository inboundOrderRepo;

    @Mock
    private OutboundOrderRepository outboundOrderRepo;

    @Mock
    private ProductQueryService productQueryService;

    @InjectMocks
    private InventoryService inventoryService;

    private InventoryStock stock;

    @BeforeEach
    void setUp() {
        stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(100);
        stock.setReservedStock(0);
        stock.setSafetyStock(10);
    }

    // ---- checkAvailability tests ----

    @Test
    @DisplayName("checkAvailability returns true when totalAvailable is strictly greater than quantity")
    void testCheckAvailability_enoughStock_returnsAvailable() {
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));

        boolean result = inventoryService.checkAvailability(100L, 50);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("checkAvailability returns false when totalAvailable equals quantity due to strict greater-than comparison")
    void testCheckAvailability_exactMatch_returnsUnavailable() {
        // onHandStock=100, reservedStock=0 -> availableStock=100
        // quantity=100, so 100 > 100 = false due to strict greater-than comparison
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));

        boolean result = inventoryService.checkAvailability(100L, 100);

        // Strict > instead of >= causes exact match to be unavailable
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("checkAvailability returns false when totalAvailable is less than quantity")
    void testCheckAvailability_insufficientStock_returnsFalse() {
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));

        boolean result = inventoryService.checkAvailability(100L, 200);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("checkAvailability sums available stock across multiple warehouses")
    void testCheckAvailability_sumsAcrossWarehouses() {
        InventoryStock stock2 = new InventoryStock();
        stock2.setId(2L);
        stock2.setWarehouseId(2L);
        stock2.setSkuId(100L);
        stock2.setOnHandStock(50);
        stock2.setReservedStock(0);

        // totalAvailable = 100 + 50 = 150 > 120 -> true
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock, stock2));

        boolean result = inventoryService.checkAvailability(100L, 120);

        assertThat(result).isTrue();
    }

    // ---- getStockSummary tests ----

    @Test
    @DisplayName("getStockSummary returns correct available and reserved totals")
    void testGetStockSummary_returnsCorrectData() {
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));

        StockSummaryDto result = inventoryService.getStockSummary(100L);

        assertThat(result.getAvailableStock()).isEqualTo(100);
        assertThat(result.getReservedStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("getStockSummary aggregates stock across multiple warehouses")
    void testGetStockSummary_aggregatesAcrossWarehouses() {
        InventoryStock stock2 = new InventoryStock();
        stock2.setId(2L);
        stock2.setWarehouseId(2L);
        stock2.setSkuId(100L);
        stock2.setOnHandStock(50);
        stock2.setReservedStock(10);

        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock, stock2));

        StockSummaryDto result = inventoryService.getStockSummary(100L);

        // availableStock = (100-0) + (50-10) = 100 + 40 = 140
        assertThat(result.getAvailableStock()).isEqualTo(140);
        // reservedStock = 0 + 10 = 10
        assertThat(result.getReservedStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("getStockSummary returns zero when no stock exists")
    void testGetStockSummary_noStockReturnsZero() {
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of());

        StockSummaryDto result = inventoryService.getStockSummary(100L);

        assertThat(result.getAvailableStock()).isEqualTo(0);
        assertThat(result.getReservedStock()).isEqualTo(0);
    }

    // ---- getStockSummaryResponse tests ----

    @Test
    @DisplayName("getStockSummaryResponse returns correct fields including onHandStock")
    void testGetStockSummaryResponse_returnsCorrectData() {
        SkuDto skuDto = new SkuDto();
        skuDto.setSkuId(100L);
        skuDto.setName("Test Product");

        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));
        when(productQueryService.getSku(100L)).thenReturn(skuDto);

        StockSummaryResponse response = inventoryService.getStockSummaryResponse(100L);

        assertThat(response.getSkuId()).isEqualTo(100L);
        assertThat(response.getSkuName()).isEqualTo("Test Product");
        assertThat(response.getOnHandStock()).isEqualTo(100);
        assertThat(response.getReservedStock()).isEqualTo(0);
        assertThat(response.getAvailableStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("getStockSummaryResponse sets skuName to null when productQueryService returns null")
    void testGetStockSummaryResponse_nullSkuNameWhenSkuNotFound() {
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));
        when(productQueryService.getSku(100L)).thenReturn(null);

        StockSummaryResponse response = inventoryService.getStockSummaryResponse(100L);

        assertThat(response.getSkuName()).isNull();
    }

    // ---- checkAndReport tests ----

    @Test
    @DisplayName("checkAndReport returns InventoryCheckResponse with available status and stock count")
    void testCheckAndReport_returnsCheckResponse() {
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock));

        InventoryCheckResponse response = inventoryService.checkAndReport(100L, 50);

        assertThat(response.getSkuId()).isEqualTo(100L);
        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getAvailableStock()).isEqualTo(100);
    }

    // ---- inbound tests ----

    @Test
    @DisplayName("inbound increases onHandStock for existing stock")
    void testInbound_increasesOnHandStock() {
        InboundRequest request = new InboundRequest();
        request.setWarehouseId(1L);
        request.setSkuId(100L);
        request.setQuantity(30);

        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));
        when(stockRepo.save(any(InventoryStock.class))).thenReturn(stock);
        when(inboundOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryStock result = inventoryService.inbound(request);

        assertThat(result.getOnHandStock()).isEqualTo(130); // 100 + 30
    }

    @Test
    @DisplayName("inbound creates new stock record when none exists")
    void testInbound_createsNewStockWhenNoneExists() {
        InboundRequest request = new InboundRequest();
        request.setWarehouseId(2L);
        request.setSkuId(200L);
        request.setQuantity(50);

        when(stockRepo.findByWarehouseIdAndSkuId(2L, 200L)).thenReturn(Optional.empty());
        when(stockRepo.save(any(InventoryStock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inboundOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryStock result = inventoryService.inbound(request);

        assertThat(result.getOnHandStock()).isEqualTo(50);
        assertThat(result.getWarehouseId()).isEqualTo(2L);
        assertThat(result.getSkuId()).isEqualTo(200L);
        assertThat(result.getReservedStock()).isEqualTo(0);
        assertThat(result.getSafetyStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("inbound saves an InboundOrder with COMPLETED status")
    void testInbound_savesInboundOrder() {
        InboundRequest request = new InboundRequest();
        request.setWarehouseId(1L);
        request.setSkuId(100L);
        request.setQuantity(20);

        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));
        when(stockRepo.save(any(InventoryStock.class))).thenReturn(stock);
        when(inboundOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.inbound(request);

        ArgumentCaptor<com.ecommerce.inventory.entity.InboundOrder> captor =
                ArgumentCaptor.forClass(com.ecommerce.inventory.entity.InboundOrder.class);
        verify(inboundOrderRepo).save(captor.capture());
        assertThat(captor.getValue().getWarehouseId()).isEqualTo(1L);
        assertThat(captor.getValue().getSkuId()).isEqualTo(100L);
        assertThat(captor.getValue().getQuantity()).isEqualTo(20);
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().getOrderNo()).startsWith("IB");
    }

    // ---- outbound tests ----

    @Test
    @DisplayName("outbound decreases onHandStock when sufficient stock exists")
    void testOutbound_decreasesOnHandStock() {
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));
        when(stockRepo.save(any(InventoryStock.class))).thenReturn(stock);
        when(outboundOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryStock result = inventoryService.outbound(1L, 100L, 30, 10L);

        assertThat(result.getOnHandStock()).isEqualTo(70); // 100 - 30
    }

    @Test
    @DisplayName("outbound saves an OutboundOrder with COMPLETED status")
    void testOutbound_savesOutboundOrder() {
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));
        when(stockRepo.save(any(InventoryStock.class))).thenReturn(stock);
        when(outboundOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.outbound(1L, 100L, 30, 10L);

        ArgumentCaptor<com.ecommerce.inventory.entity.OutboundOrder> captor =
                ArgumentCaptor.forClass(com.ecommerce.inventory.entity.OutboundOrder.class);
        verify(outboundOrderRepo).save(captor.capture());
        assertThat(captor.getValue().getWarehouseId()).isEqualTo(1L);
        assertThat(captor.getValue().getSkuId()).isEqualTo(100L);
        assertThat(captor.getValue().getQuantity()).isEqualTo(30);
        assertThat(captor.getValue().getOrderId()).isEqualTo(10L);
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().getOrderNo()).startsWith("OB");
    }

    @Test
    @DisplayName("outbound throws ResourceNotFoundException when stock does not exist")
    void testOutbound_throwsWhenStockNotFound() {
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.outbound(1L, 100L, 30, 10L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(outboundOrderRepo, never()).save(any());
    }

    @Test
    @DisplayName("outbound throws BusinessException when onHandStock is insufficient")
    void testOutbound_throwsWhenInsufficientStock() {
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> inventoryService.outbound(1L, 100L, 200, 10L))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getCode().equals("INSUFFICIENT_STOCK"),
                        "should have code INSUFFICIENT_STOCK");

        verify(outboundOrderRepo, never()).save(any());
    }

    // ---- listAvailableWarehouses tests ----

    @Test
    @DisplayName("listAvailableWarehouses returns warehouse IDs with positive available stock")
    void testListAvailableWarehouses_returnsWarehousesWithStock() {
        InventoryStock stock2 = new InventoryStock();
        stock2.setId(2L);
        stock2.setWarehouseId(2L);
        stock2.setSkuId(100L);
        stock2.setOnHandStock(0);
        stock2.setReservedStock(0);

        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock, stock2));

        List<Long> result = inventoryService.listAvailableWarehouses(100L);

        assertThat(result).containsExactly(1L); // only warehouse 1 has available stock
    }
}
