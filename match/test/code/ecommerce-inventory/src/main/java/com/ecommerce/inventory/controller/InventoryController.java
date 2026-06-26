package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.InventoryCheckRequest;
import com.ecommerce.inventory.dto.InventoryCheckResponse;
import com.ecommerce.inventory.dto.StockSummaryResponse;
import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/sku/{skuId}")
    public StockSummaryResponse getSkuStock(@PathVariable Long skuId) {
        return inventoryService.getStockSummaryResponse(skuId);
    }

    @PostMapping("/check")
    public InventoryCheckResponse checkAvailability(@Valid @RequestBody InventoryCheckRequest request) {
        return inventoryService.checkAndReport(request.getSkuId(), request.getQuantity());
    }
}
