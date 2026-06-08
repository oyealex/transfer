package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.SettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {

    Optional<SettlementBatch> findByBatchNo(String batchNo);

    Optional<SettlementBatch> findByBatchDate(LocalDate batchDate);
}
