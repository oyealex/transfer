package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.InventoryStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryStockRepository extends JpaRepository<InventoryStock, Long> {

    Optional<InventoryStock> findByWarehouseIdAndSkuId(Long warehouseId, Long skuId);

    List<InventoryStock> findBySkuId(Long skuId);

    List<InventoryStock> findByWarehouseId(Long warehouseId);
}
