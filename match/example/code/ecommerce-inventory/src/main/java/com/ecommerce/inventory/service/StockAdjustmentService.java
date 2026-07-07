package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.StockAdjustment;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.StockAdjustmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(StockAdjustmentService.class);

    private final InventoryStockRepository inventoryStockRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;

    public StockAdjustmentService(InventoryStockRepository inventoryStockRepository,
                                  StockAdjustmentRepository stockAdjustmentRepository) {
        this.inventoryStockRepository = inventoryStockRepository;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
    }

    @Transactional
    public StockAdjustment create(Long warehouseId, Long skuId, int afterQty, String reason) {
        InventoryStock stock = inventoryStockRepository
                .findByWarehouseIdAndSkuId(warehouseId, skuId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "InventoryStock", "warehouse=" + warehouseId + ", sku=" + skuId));

        int beforeQty = stock.getOnHandStock();
        stock.setOnHandStock(afterQty);
        inventoryStockRepository.save(stock);

        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setWarehouseId(warehouseId);
        adjustment.setSkuId(skuId);
        adjustment.setBeforeQty(beforeQty);
        adjustment.setAfterQty(afterQty);
        adjustment.setReason(reason);
        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);

        log.info("Stock adjusted: warehouseId={}, skuId={}, {} -> {}, reason={}",
                warehouseId, skuId, beforeQty, afterQty, reason);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<StockAdjustment> list(Long warehouseId) {
        return stockAdjustmentRepository.findByWarehouseId(warehouseId);
    }
}
