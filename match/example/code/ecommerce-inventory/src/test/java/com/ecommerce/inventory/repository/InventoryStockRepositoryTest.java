package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.InventoryStock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(InventoryStockRepositoryTest.JpaAuditingConfig.class)
@DisplayName("InventoryStockRepository")
class InventoryStockRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class JpaAuditingConfig {
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryStockRepository repository;

    private InventoryStock wh1Sku100;
    private InventoryStock wh2Sku100;
    private InventoryStock wh1Sku200;

    @BeforeEach
    void setUp() {
        wh1Sku100 = createStock(1L, 100L, 200, 20, 10);
        wh2Sku100 = createStock(2L, 100L, 50, 5, 0);
        wh1Sku200 = createStock(1L, 200L, 300, 0, 5);

        entityManager.persist(wh1Sku100);
        entityManager.persist(wh2Sku100);
        entityManager.persist(wh1Sku200);
        entityManager.flush();
    }

    @Test
    @DisplayName("findBySkuId returns all stock records for a given SKU")
    void testFindBySkuId_returnsAllStockForSku() {
        List<InventoryStock> results = repository.findBySkuId(100L);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(InventoryStock::getWarehouseId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("findBySkuId returns empty list when no stock exists for SKU")
    void testFindBySkuId_returnsEmptyWhenNoMatch() {
        List<InventoryStock> results = repository.findBySkuId(999L);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("findByWarehouseIdAndSkuId returns the unique stock for a warehouse-SKU pair")
    void testFindByWarehouseIdAndSkuId_returnsStock() {
        Optional<InventoryStock> result = repository.findByWarehouseIdAndSkuId(1L, 100L);

        assertThat(result).isPresent();
        assertThat(result.get().getOnHandStock()).isEqualTo(200);
        assertThat(result.get().getReservedStock()).isEqualTo(20);
        assertThat(result.get().getSafetyStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("findByWarehouseIdAndSkuId returns empty when pair does not exist")
    void testFindByWarehouseIdAndSkuId_returnsEmptyWhenNoMatch() {
        Optional<InventoryStock> result = repository.findByWarehouseIdAndSkuId(1L, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByWarehouseId returns all stock records for a given warehouse")
    void testFindByWarehouseId_returnsAllStockForWarehouse() {
        List<InventoryStock> results = repository.findByWarehouseId(1L);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(InventoryStock::getSkuId)
                .containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    @DisplayName("findByWarehouseId returns empty list when warehouse has no stock")
    void testFindByWarehouseId_returnsEmptyWhenNoMatch() {
        List<InventoryStock> results = repository.findByWarehouseId(999L);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("save persists InventoryStock with all fields including audit timestamps")
    void testSave_persistsAllFields() {
        InventoryStock stock = new InventoryStock();
        stock.setWarehouseId(3L);
        stock.setSkuId(300L);
        stock.setOnHandStock(100);
        stock.setReservedStock(10);
        stock.setSafetyStock(5);

        InventoryStock saved = repository.save(stock);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getWarehouseId()).isEqualTo(3L);
        assertThat(saved.getSkuId()).isEqualTo(300L);
        assertThat(saved.getOnHandStock()).isEqualTo(100);
        assertThat(saved.getReservedStock()).isEqualTo(10);
        assertThat(saved.getSafetyStock()).isEqualTo(5);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("getAvailableStock computes onHandStock minus reservedStock correctly")
    void testGetAvailableStock_computesCorrectly() {
        Optional<InventoryStock> result = repository.findByWarehouseIdAndSkuId(1L, 100L);

        assertThat(result).isPresent();
        // availableStock = onHandStock - reservedStock = 200 - 20 = 180
        assertThat(result.get().getAvailableStock()).isEqualTo(180);
    }

    @Test
    @DisplayName("findBySkuId returns empty against clean database")
    void testFindBySkuId_cleanDatabase() {
        // Delete all existing records
        repository.deleteAll();
        entityManager.flush();

        List<InventoryStock> results = repository.findBySkuId(100L);
        assertThat(results).isEmpty();
    }

    private InventoryStock createStock(Long warehouseId, Long skuId, int onHand, int reserved, int safety) {
        InventoryStock stock = new InventoryStock();
        stock.setWarehouseId(warehouseId);
        stock.setSkuId(skuId);
        stock.setOnHandStock(onHand);
        stock.setReservedStock(reserved);
        stock.setSafetyStock(safety);
        return stock;
    }
}
