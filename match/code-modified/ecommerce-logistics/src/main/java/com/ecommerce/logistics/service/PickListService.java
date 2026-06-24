package com.ecommerce.logistics.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.logistics.entity.PickList;
import com.ecommerce.logistics.entity.Shipment;
import com.ecommerce.logistics.repository.PickListRepository;
import com.ecommerce.logistics.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing warehouse pick lists.
 *
 * <p>Pick lists guide warehouse staff in gathering items from shelves
 * to fulfill a shipment. Each pick list is associated with one shipment.
 */
@Service
@Transactional
public class PickListService {

    private static final Logger log = LoggerFactory.getLogger(PickListService.class);

    private final PickListRepository pickListRepository;
    private final ShipmentRepository shipmentRepository;

    public PickListService(PickListRepository pickListRepository,
                          ShipmentRepository shipmentRepository) {
        this.pickListRepository = pickListRepository;
        this.shipmentRepository = shipmentRepository;
    }

    /**
     * Create a pick list for a shipment.
     *
     * @param shipmentId  the shipment ID
     * @param warehouseId the warehouse ID
     * @param itemsJson   JSON array of items to pick
     * @param pickerId    the staff ID assigned to picking
     * @return the created pick list
     */
    public PickList createPickList(Long shipmentId, Long warehouseId,
                                   String itemsJson, Long pickerId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found: " + shipmentId));

        if (shipment.getPickListId() != null) {
            return pickListRepository.findById(shipment.getPickListId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Pick list not found: " + shipment.getPickListId()));
        }

        PickList pickList = new PickList();
        pickList.setPickListNo("PL" + System.currentTimeMillis());
        pickList.setShipmentId(shipmentId);
        pickList.setWarehouseId(warehouseId);
        pickList.setItems(itemsJson);
        pickList.setPickerId(pickerId);
        pickList.setStatus("PENDING");

        pickList = pickListRepository.save(pickList);

        shipment.setPickListId(pickList.getId());
        shipmentRepository.save(shipment);

        log.info("Pick list created: pickListId={}, pickListNo={}, shipmentId={}",
                pickList.getId(), pickList.getPickListNo(), shipmentId);

        return pickList;
    }

    /**
     * Mark a pick list as completed (all items picked).
     *
     * @param pickListId the pick list ID
     * @param pickerId   the staff ID who completed picking
     * @return the updated pick list
     */
    public PickList completePicking(Long pickListId, Long pickerId) {
        PickList pickList = pickListRepository.findById(pickListId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pick list not found: " + pickListId));

        if (!"PICKING".equals(pickList.getStatus()) && !"PENDING".equals(pickList.getStatus())) {
            throw new IllegalStateException(
                    "Cannot complete pick list in status: " + pickList.getStatus());
        }

        pickList.setStatus("COMPLETED");
        pickList.setPickerId(pickerId);
        pickList = pickListRepository.save(pickList);

        log.info("Pick list completed: pickListId={}, pickerId={}", pickListId, pickerId);

        return pickList;
    }

    /**
     * Query a pick list by ID.
     */
    @Transactional(readOnly = true)
    public PickList getPickList(Long pickListId) {
        return pickListRepository.findById(pickListId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pick list not found: " + pickListId));
    }

    /**
     * Query a pick list by shipment ID.
     */
    @Transactional(readOnly = true)
    public PickList getPickListByShipmentId(Long shipmentId) {
        return pickListRepository.findByShipmentId(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pick list not found for shipment: " + shipmentId));
    }
}
