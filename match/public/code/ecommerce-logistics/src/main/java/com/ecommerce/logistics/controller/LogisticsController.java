package com.ecommerce.logistics.controller;

import com.ecommerce.logistics.dto.LogisticsCallbackRequest;
import com.ecommerce.logistics.dto.ShipmentResponse;
import com.ecommerce.logistics.service.LogisticsCallbackService;
import com.ecommerce.logistics.service.ShipmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing logistics REST controller.
 *
 * <p>Provides endpoints for querying logistics status and receiving
 * carrier callbacks. Callback endpoint does not require user authentication.
 */
@RestController
@RequestMapping("/api/v1/logistics")
public class LogisticsController {

    private static final Logger log = LoggerFactory.getLogger(LogisticsController.class);

    private final ShipmentService shipmentService;
    private final LogisticsCallbackService callbackService;

    public LogisticsController(ShipmentService shipmentService,
                              LogisticsCallbackService callbackService) {
        this.shipmentService = shipmentService;
        this.callbackService = callbackService;
    }

    /**
     * Query logistics status for an order.
     */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ShipmentResponse> getLogisticsByOrderId(@PathVariable Long orderId) {
        log.info("GET /api/v1/logistics/order/{}", orderId);
        ShipmentResponse response = shipmentService.getShipmentByOrderId(orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Receive logistics status callback from carrier systems.
     *
     * <p>This endpoint is called by external carriers and does not
     * require user authentication. It uses signature-based verification.
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> receiveCallback(@RequestBody LogisticsCallbackRequest request) {
        log.info("POST /api/v1/logistics/callback: trackingNo={}, status={}",
                request.getTrackingNo(), request.getStatus());
        callbackService.processCallback(request);
        return ResponseEntity.ok().build();
    }
}
