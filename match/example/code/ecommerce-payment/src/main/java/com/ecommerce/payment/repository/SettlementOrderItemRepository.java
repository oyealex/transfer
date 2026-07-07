package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.SettlementOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementOrderItemRepository extends JpaRepository<SettlementOrderItem, Long> {

    List<SettlementOrderItem> findByBatchId(Long batchId);
}
