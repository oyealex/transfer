package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.InboundRequest;
import com.ecommerce.inventory.dto.StockWarningResponse;
import com.ecommerce.inventory.dto.WarehouseCreateRequest;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.OutboundOrder;
import com.ecommerce.inventory.entity.StockAdjustment;
import com.ecommerce.inventory.entity.Warehouse;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.inventory.service.StockAdjustmentService;
import com.ecommerce.inventory.service.StockWarningService;
import com.ecommerce.inventory.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminInventoryController {

    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;
    private final StockWarningService stockWarningService;
    private final StockAdjustmentService stockAdjustmentService;

    public AdminInventoryController(WarehouseService warehouseService,
                                    InventoryService inventoryService,
                                    StockWarningService stockWarningService,
                                    StockAdjustmentService stockAdjustmentService) {
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
        this.stockWarningService = stockWarningService;
        this.stockAdjustmentService = stockAdjustmentService;
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public Warehouse createWarehouse(@Valid @RequestBody WarehouseCreateRequest request) {
        return warehouseService.create(request);
    }

    @PostMapping("/inventory/inbound")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryStock inbound(@Valid @RequestBody InboundRequest request) {
        return inventoryService.inbound(request);
    }

    @PostMapping("/inventory/outbound")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryStock outbound(@RequestParam Long warehouseId,
                                   @RequestParam Long skuId,
                                   @RequestParam int quantity,
                                   @RequestParam(required = false) Long orderId) {
        return inventoryService.outbound(warehouseId, skuId, quantity, orderId);
    }

    @PostMapping("/inventory/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public StockAdjustment createAdjustment(@RequestParam Long warehouseId,
                                            @RequestParam Long skuId,
                                            @RequestParam int afterQty,
                                            @RequestParam String reason) {
        return stockAdjustmentService.create(warehouseId, skuId, afterQty, reason);
    }

    @GetMapping("/inventory/adjustments")
    public List<StockAdjustment> listAdjustments(@RequestParam Long warehouseId) {
        return stockAdjustmentService.list(warehouseId);
    }

    @GetMapping("/inventory/warnings")
    public List<StockWarningResponse> getWarnings() {
        return stockWarningService.getWarnings();
    }

    @PostMapping("/inventory/warnings/rule")
    public void setWarningRule(@RequestParam Long skuId,
                               @RequestParam(required = false) Long warehouseId,
                               @RequestParam int warningThreshold) {
        stockWarningService.setWarningRule(skuId, warehouseId, warningThreshold);
    }
}
