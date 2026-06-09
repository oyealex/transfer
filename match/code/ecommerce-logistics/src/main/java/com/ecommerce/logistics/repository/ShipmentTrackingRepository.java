package com.ecommerce.logistics.repository;

import com.ecommerce.logistics.entity.ShipmentTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link ShipmentTracking} entities.
 */
@Repository
public interface ShipmentTrackingRepository extends JpaRepository<ShipmentTracking, Long> {

    /**
     * Find all tracking records for a shipment, ordered by event time.
     */
    List<ShipmentTracking> findByShipmentIdOrderByEventTimeAsc(Long shipmentId);
}
