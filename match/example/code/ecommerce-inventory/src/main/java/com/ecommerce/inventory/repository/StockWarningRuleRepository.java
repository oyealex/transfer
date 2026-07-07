package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.StockWarningRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockWarningRuleRepository extends JpaRepository<StockWarningRule, Long> {

    List<StockWarningRule> findByEnabledTrue();

    Optional<StockWarningRule> findBySkuIdAndWarehouseId(Long skuId, Long warehouseId);
}
