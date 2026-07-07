package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.StockWarningResponse;
import com.ecommerce.inventory.entity.InventoryStock;
import com.ecommerce.inventory.entity.StockWarningRule;
import com.ecommerce.inventory.repository.InventoryStockRepository;
import com.ecommerce.inventory.repository.StockWarningRuleRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("StockWarningService")
@ExtendWith(MockitoExtension.class)
class StockWarningServiceTest {

    @Mock
    private InventoryStockRepository stockRepo;

    @Mock
    private StockWarningRuleRepository warningRuleRepo;

    @InjectMocks
    private StockWarningService warningService;

    // ---- getWarnings tests ----

    @Test
    @DisplayName("getWarnings returns warning when onHandStock is below threshold")
    void testGetWarnings_belowThreshold_returnsWarning() {
        StockWarningRule rule = new StockWarningRule();
        rule.setId(1L);
        rule.setSkuId(100L);
        rule.setWarehouseId(1L);
        rule.setWarningThreshold(20);
        rule.setEnabled(true);

        InventoryStock stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(10);
        stock.setReservedStock(0);
        stock.setSafetyStock(5);

        when(warningRuleRepo.findByEnabledTrue()).thenReturn(List.of(rule));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));

        List<StockWarningResponse> warnings = warningService.getWarnings();

        assertThat(warnings).hasSize(1);
        StockWarningResponse warning = warnings.get(0);
        assertThat(warning.getSkuId()).isEqualTo(100L);
        assertThat(warning.getWarehouseId()).isEqualTo(1L);
        assertThat(warning.getOnHandStock()).isEqualTo(10);
        assertThat(warning.getSafetyStock()).isEqualTo(5);
        assertThat(warning.getWarningThreshold()).isEqualTo(20);
        assertThat(warning.getMessage()).contains("below warning threshold");
    }

    @Test
    @DisplayName("getWarnings returns warning when onHandStock equals threshold (<=) ")
    void testGetWarnings_atThreshold_returnsWarning() {
        StockWarningRule rule = new StockWarningRule();
        rule.setId(1L);
        rule.setSkuId(100L);
        rule.setWarehouseId(1L);
        rule.setWarningThreshold(20);
        rule.setEnabled(true);

        InventoryStock stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(20); // exactly at threshold
        stock.setSafetyStock(5);

        when(warningRuleRepo.findByEnabledTrue()).thenReturn(List.of(rule));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));

        List<StockWarningResponse> warnings = warningService.getWarnings();

        // onHandStock <= threshold triggers warning
        assertThat(warnings).hasSize(1);
    }

    @Test
    @DisplayName("getWarnings does not return warning when onHandStock is above threshold")
    void testGetWarnings_aboveThreshold_noWarning() {
        StockWarningRule rule = new StockWarningRule();
        rule.setId(1L);
        rule.setSkuId(100L);
        rule.setWarehouseId(1L);
        rule.setWarningThreshold(20);
        rule.setEnabled(true);

        InventoryStock stock = new InventoryStock();
        stock.setId(1L);
        stock.setWarehouseId(1L);
        stock.setSkuId(100L);
        stock.setOnHandStock(50); // above threshold
        stock.setSafetyStock(5);

        when(warningRuleRepo.findByEnabledTrue()).thenReturn(List.of(rule));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.of(stock));

        List<StockWarningResponse> warnings = warningService.getWarnings();

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("getWarnings queries all SKUs across warehouses when rule has no warehouseId")
    void testGetWarnings_globalRule_checksAllWarehouses() {
        StockWarningRule rule = new StockWarningRule();
        rule.setId(1L);
        rule.setSkuId(100L);
        rule.setWarehouseId(null); // global rule
        rule.setWarningThreshold(30);
        rule.setEnabled(true);

        InventoryStock stock1 = new InventoryStock();
        stock1.setId(1L);
        stock1.setWarehouseId(1L);
        stock1.setSkuId(100L);
        stock1.setOnHandStock(25);

        InventoryStock stock2 = new InventoryStock();
        stock2.setId(2L);
        stock2.setWarehouseId(2L);
        stock2.setSkuId(100L);
        stock2.setOnHandStock(40);

        when(warningRuleRepo.findByEnabledTrue()).thenReturn(List.of(rule));
        when(stockRepo.findBySkuId(100L)).thenReturn(List.of(stock1, stock2));

        List<StockWarningResponse> warnings = warningService.getWarnings();

        // Only warehouse 1 should trigger (25 <= 30)
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getWarehouseId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getWarnings returns empty list when no rules are enabled")
    void testGetWarnings_noEnabledRules_returnsEmpty() {
        when(warningRuleRepo.findByEnabledTrue()).thenReturn(List.of());

        List<StockWarningResponse> warnings = warningService.getWarnings();

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("getWarnings skips missing stock for warehouse-specific rule")
    void testGetWarnings_missingStock_noWarning() {
        StockWarningRule rule = new StockWarningRule();
        rule.setId(1L);
        rule.setSkuId(100L);
        rule.setWarehouseId(1L);
        rule.setWarningThreshold(20);
        rule.setEnabled(true);

        when(warningRuleRepo.findByEnabledTrue()).thenReturn(List.of(rule));
        when(stockRepo.findByWarehouseIdAndSkuId(1L, 100L)).thenReturn(Optional.empty());

        List<StockWarningResponse> warnings = warningService.getWarnings();

        assertThat(warnings).isEmpty();
    }

    // ---- setWarningRule tests ----

    @Test
    @DisplayName("setWarningRule creates new rule when none exists")
    void testSetWarningRule_createsNewRule() {
        when(warningRuleRepo.findBySkuIdAndWarehouseId(100L, 1L)).thenReturn(Optional.empty());
        when(warningRuleRepo.save(any(StockWarningRule.class))).thenAnswer(inv -> {
            StockWarningRule r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        StockWarningRule result = warningService.setWarningRule(100L, 1L, 15);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSkuId()).isEqualTo(100L);
        assertThat(result.getWarehouseId()).isEqualTo(1L);
        assertThat(result.getWarningThreshold()).isEqualTo(15);
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("setWarningRule updates existing rule")
    void testSetWarningRule_updatesExistingRule() {
        StockWarningRule existing = new StockWarningRule();
        existing.setId(5L);
        existing.setSkuId(100L);
        existing.setWarehouseId(1L);
        existing.setWarningThreshold(10);
        existing.setEnabled(false);

        when(warningRuleRepo.findBySkuIdAndWarehouseId(100L, 1L)).thenReturn(Optional.of(existing));
        when(warningRuleRepo.save(any(StockWarningRule.class))).thenAnswer(inv -> inv.getArgument(0));

        StockWarningRule result = warningService.setWarningRule(100L, 1L, 25);

        assertThat(result.getWarningThreshold()).isEqualTo(25);
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("setWarningRule with null warehouseId is allowed")
    void testSetWarningRule_nullWarehouseId() {
        when(warningRuleRepo.findBySkuIdAndWarehouseId(100L, null)).thenReturn(Optional.empty());
        when(warningRuleRepo.save(any(StockWarningRule.class))).thenAnswer(inv -> {
            StockWarningRule r = inv.getArgument(0);
            r.setId(2L);
            return r;
        });

        StockWarningRule result = warningService.setWarningRule(100L, null, 30);

        assertThat(result.getSkuId()).isEqualTo(100L);
        assertThat(result.getWarehouseId()).isNull();
        assertThat(result.getWarningThreshold()).isEqualTo(30);
    }
}
