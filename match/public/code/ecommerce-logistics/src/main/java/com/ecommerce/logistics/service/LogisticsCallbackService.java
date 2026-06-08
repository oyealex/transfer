package com.ecommerce.logistics.service;

import com.ecommerce.logistics.dto.LogisticsCallbackRequest;
import com.ecommerce.logistics.entity.Shipment;
import com.ecommerce.logistics.entity.ShipmentStatus;
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

    public LogisticsCallbackService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
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
