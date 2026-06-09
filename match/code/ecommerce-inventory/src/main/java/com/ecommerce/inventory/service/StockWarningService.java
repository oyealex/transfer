package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.StockWarningResponse;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.StockWarningRule;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.StockWarningRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class StockWarningService {

    private static final Logger log = LoggerFactory.getLogger(StockWarningService.class);

    private final InventoryStockRepository inventoryStockRepository;
    private final StockWarningRuleRepository stockWarningRuleRepository;

    public StockWarningService(InventoryStockRepository inventoryStockRepository,
                               StockWarningRuleRepository stockWarningRuleRepository) {
        this.inventoryStockRepository = inventoryStockRepository;
        this.stockWarningRuleRepository = stockWarningRuleRepository;
    }

    @Transactional(readOnly = true)
    public List<StockWarningResponse> getWarnings() {
        List<StockWarningRule> rules = stockWarningRuleRepository.findByEnabledTrue();
        List<StockWarningResponse> warnings = new ArrayList<>();

        for (StockWarningRule rule : rules) {
            List<InventoryStock> stocks;
            if (rule.getWarehouseId() != null) {
                stocks = inventoryStockRepository.findByWarehouseIdAndSkuId(
                        rule.getWarehouseId(), rule.getSkuId())
                        .map(List::of)
                        .orElse(List.of());
            } else {
                stocks = inventoryStockRepository.findBySkuId(rule.getSkuId());
            }

            for (InventoryStock stock : stocks) {
                if (stock.getOnHandStock() <= rule.getWarningThreshold()) {
                    StockWarningResponse response = new StockWarningResponse();
                    response.setSkuId(stock.getSkuId());
                    response.setWarehouseId(stock.getWarehouseId());
                    response.setOnHandStock(stock.getOnHandStock());
                    response.setSafetyStock(stock.getSafetyStock());
                    response.setWarningThreshold(rule.getWarningThreshold());
                    response.setMessage(String.format(
                            "SKU %d in warehouse %d is below warning threshold: %d <= %d",
                            stock.getSkuId(), stock.getWarehouseId(),
                            stock.getOnHandStock(), rule.getWarningThreshold()));
                    warnings.add(response);
                }
            }
        }
        log.debug("Stock warnings found: {}", warnings.size());
        return warnings;
    }

    @Transactional
    public StockWarningRule setWarningRule(Long skuId, Long warehouseId, int warningThreshold) {
        StockWarningRule rule = stockWarningRuleRepository
                .findBySkuIdAndWarehouseId(skuId, warehouseId)
                .orElseGet(StockWarningRule::new);

        rule.setSkuId(skuId);
        rule.setWarehouseId(warehouseId);
        rule.setWarningThreshold(warningThreshold);
        rule.setEnabled(true);
        StockWarningRule saved = stockWarningRuleRepository.save(rule);
        log.info("Warning rule set: skuId={}, warehouseId={}, threshold={}",
                skuId, warehouseId, warningThreshold);
        return saved;
    }
}
