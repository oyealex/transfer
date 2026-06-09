package com.ecommerce.logistics.repository;

import com.ecommerce.logistics.entity.LabelRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link LabelRecord} entities.
 */
@Repository
public interface LabelRecordRepository extends JpaRepository<LabelRecord, Long> {

    /**
     * Find all label records for a shipment.
     */
    List<LabelRecord> findByShipmentId(Long shipmentId);
}
