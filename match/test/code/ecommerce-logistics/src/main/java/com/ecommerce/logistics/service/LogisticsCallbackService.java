package com.ecommerce.logistics.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.logistics.dto.LogisticsCallbackRequest;
import com.ecommerce.logistics.entity.Shipment;
import com.ecommerce.logistics.entity.ShipmentStatus;
import com.ecommerce.logistics.event.ShipmentDeliveredEvent;
import com.ecommerce.logistics.query.OrderLogisticsStatusUpdater;
import com.ecommerce.logistics.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles logistics status callbacks from external carrier systems.
 *
 * <p>Carriers call the callback endpoint to report shipment status changes
 * such as pickup, in-transit, delivery, or exception events.
 */
@Service
public class LogisticsCallbackService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsCallbackService.class);

    private final ShipmentRepository shipmentRepository;
    private final OrderLogisticsStatusUpdater orderLogisticsStatusUpdater;
    private final DomainEventPublisher eventPublisher;

    public LogisticsCallbackService(ShipmentRepository shipmentRepository,
                                     OrderLogisticsStatusUpdater orderLogisticsStatusUpdater,
                                     DomainEventPublisher eventPublisher) {
        this.shipmentRepository = shipmentRepository;
        this.orderLogisticsStatusUpdater = orderLogisticsStatusUpdater;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a logistics status callback from a carrier.
     *
     * @param request the callback request from the carrier
     */
    public void processCallback(LogisticsCallbackRequest request) {
        log.info("Received logistics callback: trackingNo={}, status={}, location={}, "
                        + "description={}, eventTime={}, signature={}",
                request.getTrackingNo(), request.getStatus(),
                request.getLocation(), request.getDescription(),
                request.getEventTime(), request.getSignature());

        // L2-07: Actually process the callback — lookup shipment and update status
        if (request.getTrackingNo() == null || request.getTrackingNo().isBlank()) {
            log.warn("Logistics callback missing trackingNo, ignoring");
            return;
        }

        ShipmentStatus newStatus = mapToShipmentStatus(request.getStatus());
        if (newStatus == null) {
            log.warn("Unknown logistics callback status: {}", request.getStatus());
            return;
        }

        var shipments = shipmentRepository.findByTrackingNo(request.getTrackingNo());
        if (shipments.isEmpty()) {
            log.warn("No shipment found for trackingNo={}", request.getTrackingNo());
            return;
        }

        for (Shipment shipment : shipments) {
            shipment.setStatus(newStatus);
            shipmentRepository.save(shipment);

            // Update order logistics status
            try {
                orderLogisticsStatusUpdater.updateLogisticsStatus(
                        shipment.getOrderId(), newStatus.name());
            } catch (Exception e) {
                log.warn("Failed to update order logistics status: {}", e.getMessage());
            }

            // Publish event for DELIVERED status
            if (newStatus == ShipmentStatus.DELIVERED) {
                try {
                    eventPublisher.publish(new ShipmentDeliveredEvent(
                            this, shipment.getOrderId(), shipment.getId(),
                            java.time.LocalDateTime.now()));
                } catch (Exception e) {
                    log.warn("Failed to publish ShipmentDeliveredEvent: {}", e.getMessage());
                }
            }

            log.info("Updated shipment {} to status {} via callback",
                    shipment.getId(), newStatus);
        }
    }

    /**
     * Map a carrier status string to a ShipmentStatus enum.
     */
    private ShipmentStatus mapToShipmentStatus(String status) {
        if (status == null) {
            return ShipmentStatus.EXCEPTION;
        }
        switch (status.toUpperCase()) {
            case "COLLECTED":
                return ShipmentStatus.COLLECTED;
            case "IN_TRANSIT":
                return ShipmentStatus.IN_TRANSIT;
            case "DELIVERED":
                return ShipmentStatus.DELIVERED;
            case "EXCEPTION":
                return ShipmentStatus.EXCEPTION;
            default:
                log.warn("Unknown carrier status: {}, defaulting to IN_TRANSIT", status);
                return ShipmentStatus.IN_TRANSIT;
        }
    }
}
