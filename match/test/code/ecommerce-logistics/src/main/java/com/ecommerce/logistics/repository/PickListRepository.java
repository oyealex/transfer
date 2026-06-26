package com.ecommerce.logistics.repository;

import com.ecommerce.logistics.entity.PickList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link PickList} entities.
 */
@Repository
public interface PickListRepository extends JpaRepository<PickList, Long> {

    /**
     * Find a pick list by its associated shipment ID.
     */
    Optional<PickList> findByShipmentId(Long shipmentId);
}
