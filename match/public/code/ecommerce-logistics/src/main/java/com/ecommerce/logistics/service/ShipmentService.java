package com.ecommerce.logistics.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.logistics.dto.ShipmentResponse;
import com.ecommerce.logistics.dto.TrackingResponse;
import com.ecommerce.logistics.entity.LabelRecord;
import com.ecommerce.logistics.entity.PickList;
import com.ecommerce.logistics.entity.Shipment;
import com.ecommerce.logistics.entity.ShipmentStatus;
import com.ecommerce.logistics.entity.ShipmentTracking;
import com.ecommerce.logistics.query.OrderLogisticsStatusUpdater;
import com.ecommerce.logistics.repository.LabelRecordRepository;
import com.ecommerce.logistics.repository.PickListRepository;
import com.ecommerce.logistics.repository.ShipmentRepository;
import com.ecommerce.logistics.repository.ShipmentTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core service for shipment lifecycle management.
 *
 * <p>The correct shipment flow per the design specification is:
 * <ol>
 *   <li>CREATED — shipment created after payment</li>
 *   <li>PICKING — pick list generated, warehouse staff picking</li>
 *   <li>LABEL_PRINTED — shipping label printed</li>
 *   <li>OUTBOUND — scanned and leaves warehouse</li>
 *   <li>COLLECTED — carrier picks up</li>
 *   <li>IN_TRANSIT — in transportation</li>
 *   <li>DELIVERED — delivered to recipient</li>
 * </ol>
 *
 * <p>The {@link #createShipment(Long, Long, BigDecimal, String)}
 * method skips the PICKING and LABEL_PRINTED stages entirely and sets the shipment
 * status directly to OUTBOUND. Per the design specification:
 * "不得跳过拣货单和面单步骤直接出库" — pick list and label printing steps
 * must not be skipped before outbound.
 */
@Service
@Transactional
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRepository shipmentRepository;
    private final PickListRepository pickListRepository;
    private final LabelRecordRepository labelRecordRepository;
    private final ShipmentTrackingRepository trackingRepository;
    private final FreightCalculator freightCalculator;
    private final OrderLogisticsStatusUpdater orderLogisticsStatusUpdater;

    public ShipmentService(ShipmentRepository shipmentRepository,
                          PickListRepository pickListRepository,
                          LabelRecordRepository labelRecordRepository,
                          ShipmentTrackingRepository trackingRepository,
                          FreightCalculator freightCalculator,
                          OrderLogisticsStatusUpdater orderLogisticsStatusUpdater) {
        this.shipmentRepository = shipmentRepository;
        this.pickListRepository = pickListRepository;
        this.labelRecordRepository = labelRecordRepository;
        this.trackingRepository = trackingRepository;
        this.freightCalculator = freightCalculator;
        this.orderLogisticsStatusUpdater = orderLogisticsStatusUpdater;
    }

    /**
     * Create a new shipment for an order.
     *
     * <p>The shipment status is set directly to OUTBOUND instead of
     * following the expected progression: CREATED → PICKING → LABEL_PRINTED → OUTBOUND.
     * The pick list and label printing steps are entirely skipped.
     *
     * @param orderId         the order ID
     * @param userId          the user ID
     * @param freightAmount   the freight amount
     * @param addressSnapshot JSON snapshot of the delivery address
     * @return the created shipment
     */
    public Shipment createShipment(Long orderId, Long userId,
                                   BigDecimal freightAmount, String addressSnapshot) {
        // Fault injection check
        if (com.ecommerce.common.test.FaultInjectionRegistry.isActive("logistics-create-shipment-failure")) {
            throw new RuntimeException("Fault injected: logistics-create-shipment-failure");
        }

        log.info("Creating shipment for orderId={}, userId={}", orderId, userId);

        Shipment shipment = new Shipment();
        shipment.setShipmentNo(generateShipmentNo());
        shipment.setOrderId(orderId);
        shipment.setUserId(userId);
        // Status is set directly to OUTBOUND, skipping PICKING and LABEL_PRINTED.
        shipment.setStatus(ShipmentStatus.OUTBOUND);
        shipment.setFreightAmount(freightAmount != null ? freightAmount : BigDecimal.ZERO);
        shipment.setAddressSnapshot(addressSnapshot);

        shipment = shipmentRepository.save(shipment);

        // Record tracking event — also skips intermediate steps
        recordTracking(shipment.getId(), "OUTBOUND", "Warehouse",
                "Shipment created and directly outbound", null);

        // Update order logistics status
        try {
            orderLogisticsStatusUpdater.updateLogisticsStatus(orderId, "OUTBOUND");
        } catch (Exception e) {
            log.warn("Failed to update order logistics status for orderId={}: {}", orderId, e.getMessage());
        }

        log.info("Shipment created: shipmentId={}, shipmentNo={}, status={}",
                shipment.getId(), shipment.getShipmentNo(), shipment.getStatus());
        return shipment;
    }

    /**
     * Query shipment by order ID.
     */
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentByOrderId(Long orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found for order: " + orderId));

        return buildResponse(shipment);
    }

    /**
     * Query shipment by shipment number.
     */
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentByShipmentNo(String shipmentNo) {
        Shipment shipment = shipmentRepository.findByShipmentNo(shipmentNo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found: " + shipmentNo));

        return buildResponse(shipment);
    }

    /**
     * Generate a pick list for the shipment and start picking.
     *
     * @param shipmentId the shipment ID
     * @param pickerId   the staff ID performing picking
     */
    public void pick(Long shipmentId, Long pickerId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found: " + shipmentId));

        if (shipment.getStatus() != ShipmentStatus.OUTBOUND
                && shipment.getStatus() != ShipmentStatus.CREATED
                && shipment.getStatus() != ShipmentStatus.PICKING) {
            throw new IllegalStateException(
                    "Cannot pick shipment in status " + shipment.getStatus());
        }

        // Update to PICKING if not already
        if (shipment.getStatus() != ShipmentStatus.PICKING) {
            shipment.setStatus(ShipmentStatus.PICKING);
        }

        // Create pick list if not exists
        if (shipment.getPickListId() == null) {
            PickList pickList = new PickList();
            pickList.setPickListNo("PL" + System.currentTimeMillis());
            pickList.setShipmentId(shipmentId);
            pickList.setPickerId(pickerId);
            pickList.setStatus("PICKING");
            pickList = pickListRepository.save(pickList);
            shipment.setPickListId(pickList.getId());
        }

        shipmentRepository.save(shipment);
        recordTracking(shipmentId, "PICKING", "Warehouse",
                "Picking started by operator " + pickerId, pickerId.toString());

        try {
            orderLogisticsStatusUpdater.updateLogisticsStatus(
                    shipment.getOrderId(), "PICKING");
        } catch (Exception e) {
            log.warn("Failed to update order logistics status: {}", e.getMessage());
        }

        log.info("Picking started for shipmentId={} by pickerId={}", shipmentId, pickerId);
    }

    /**
     * Print shipping label for the shipment.
     *
     * @param shipmentId the shipment ID
     * @param carrier    the carrier name
     */
    public void printLabel(Long shipmentId, String carrier) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found: " + shipmentId));

        String labelNo = "LB" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String trackingNo = "TN" + System.currentTimeMillis();

        LabelRecord label = new LabelRecord();
        label.setShipmentId(shipmentId);
        label.setLabelNo(labelNo);
        label.setCarrier(carrier);
        label.setTrackingNo(trackingNo);
        label.setPrintedAt(LocalDateTime.now());
        labelRecordRepository.save(label);

        shipment.setLabelNo(labelNo);
        shipment.setTrackingNo(trackingNo);
        shipment.setCarrier(carrier);
        shipment.setStatus(ShipmentStatus.LABEL_PRINTED);
        shipmentRepository.save(shipment);

        recordTracking(shipmentId, "LABEL_PRINTED", "Warehouse",
                "Label printed: " + labelNo + ", carrier: " + carrier + ", tracking: " + trackingNo,
                null);

        try {
            orderLogisticsStatusUpdater.updateLogisticsStatus(
                    shipment.getOrderId(), "LABEL_PRINTED");
        } catch (Exception e) {
            log.warn("Failed to update order logistics status: {}", e.getMessage());
        }

        log.info("Label printed for shipmentId={}: labelNo={}, trackingNo={}, carrier={}",
                shipmentId, labelNo, trackingNo, carrier);
    }

    /**
     * Complete outbound scanning — the package leaves the warehouse.
     *
     * @param shipmentId the shipment ID
     */
    public void outbound(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found: " + shipmentId));

        shipment.setStatus(ShipmentStatus.OUTBOUND);
        shipmentRepository.save(shipment);

        recordTracking(shipmentId, "OUTBOUND", "Warehouse Exit",
                "Package scanned and left warehouse", null);

        try {
            orderLogisticsStatusUpdater.updateLogisticsStatus(
                    shipment.getOrderId(), "OUTBOUND");
        } catch (Exception e) {
            log.warn("Failed to update order logistics status: {}", e.getMessage());
        }

        log.info("Outbound completed for shipmentId={}", shipmentId);
    }

    /**
     * Update shipment status after carrier callback.
     */
    public void updateStatus(Long shipmentId, ShipmentStatus newStatus,
                            String location, String description) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shipment not found: " + shipmentId));

        shipment.setStatus(newStatus);

        if (newStatus == ShipmentStatus.COLLECTED) {
            shipment.setPickupTime(LocalDateTime.now());
        } else if (newStatus == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(LocalDateTime.now());
        }

        shipmentRepository.save(shipment);

        recordTracking(shipmentId, newStatus.name(), location, description, "CARRIER");

        try {
            orderLogisticsStatusUpdater.updateLogisticsStatus(
                    shipment.getOrderId(), newStatus.name());
        } catch (Exception e) {
            log.warn("Failed to update order logistics status: {}", e.getMessage());
        }

        log.info("Shipment {} status updated to {}", shipmentId, newStatus);
    }

    // ======================== Private helpers ========================

    private String generateShipmentNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seqPart = String.format("%04d", System.currentTimeMillis() % 10000);
        return "SH" + datePart + seqPart;
    }

    private void recordTracking(Long shipmentId, String status, String location,
                                String description, String operator) {
        ShipmentTracking tracking = new ShipmentTracking();
        tracking.setShipmentId(shipmentId);
        tracking.setStatus(status);
        tracking.setLocation(location);
        tracking.setDescription(description);
        tracking.setEventTime(LocalDateTime.now());
        tracking.setOperator(operator != null ? operator : "SYSTEM");
        trackingRepository.save(tracking);
    }

    private ShipmentResponse buildResponse(Shipment shipment) {
        ShipmentResponse resp = new ShipmentResponse();
        resp.setId(shipment.getId());
        resp.setShipmentNo(shipment.getShipmentNo());
        resp.setOrderId(shipment.getOrderId());
        resp.setUserId(shipment.getUserId());
        resp.setStatus(shipment.getStatus().name());
        resp.setPickListId(shipment.getPickListId());
        resp.setLabelNo(shipment.getLabelNo());
        resp.setTrackingNo(shipment.getTrackingNo());
        resp.setCarrier(shipment.getCarrier());
        resp.setFreightAmount(shipment.getFreightAmount());
        resp.setPickupTime(shipment.getPickupTime());
        resp.setDeliveredAt(shipment.getDeliveredAt());
        resp.setAddressSnapshot(shipment.getAddressSnapshot());
        resp.setCreatedAt(shipment.getCreatedAt());

        List<ShipmentTracking> trackings = trackingRepository
                .findByShipmentIdOrderByEventTimeAsc(shipment.getId());
        List<TrackingResponse> trackingDtos = trackings.stream().map(t -> {
            TrackingResponse dto = new TrackingResponse();
            dto.setId(t.getId());
            dto.setShipmentId(t.getShipmentId());
            dto.setStatus(t.getStatus());
            dto.setLocation(t.getLocation());
            dto.setDescription(t.getDescription());
            dto.setEventTime(t.getEventTime());
            dto.setOperator(t.getOperator());
            return dto;
        }).collect(Collectors.toList());
        resp.setTrackingRecords(trackingDtos);

        return resp;
    }
}
