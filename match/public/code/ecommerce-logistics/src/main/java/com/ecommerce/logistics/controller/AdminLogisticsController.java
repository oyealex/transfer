package com.ecommerce.logistics.controller;

import com.ecommerce.logistics.dto.FreightTemplateRequest;
import com.ecommerce.logistics.entity.FreightTemplate;
import com.ecommerce.logistics.service.FreightTemplateService;
import com.ecommerce.logistics.service.ShipmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-facing logistics REST controller.
 *
 * <p>Provides endpoints for warehouse operations such as picking,
 * label printing, outbound scanning, and freight template management.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/logistics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLogisticsController {

    private static final Logger log = LoggerFactory.getLogger(AdminLogisticsController.class);

    private final ShipmentService shipmentService;
    private final FreightTemplateService freightTemplateService;

    public AdminLogisticsController(ShipmentService shipmentService,
                                   FreightTemplateService freightTemplateService) {
        this.shipmentService = shipmentService;
        this.freightTemplateService = freightTemplateService;
    }

    /**
     * Generate a pick list and start picking for a shipment.
     */
    @PostMapping("/shipments/{id}/pick")
    public ResponseEntity<Void> pick(@PathVariable Long id) {
        log.info("POST /api/v1/admin/logistics/shipments/{}/pick", id);
        shipmentService.pick(id, null);
        return ResponseEntity.ok().build();
    }

    /**
     * Print a shipping label for a shipment.
     */
    @PostMapping("/shipments/{id}/print-label")
    public ResponseEntity<Void> printLabel(@PathVariable Long id) {
        log.info("POST /api/v1/admin/logistics/shipments/{}/print-label", id);
        shipmentService.printLabel(id, "DEFAULT");
        return ResponseEntity.ok().build();
    }

    /**
     * Complete outbound scanning for a shipment.
     */
    @PostMapping("/shipments/{id}/outbound")
    public ResponseEntity<Void> outbound(@PathVariable Long id) {
        log.info("POST /api/v1/admin/logistics/shipments/{}/outbound", id);
        shipmentService.outbound(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Create a new freight template.
     */
    @PostMapping("/freight-templates")
    public ResponseEntity<FreightTemplate> createFreightTemplate(
            @Valid @RequestBody FreightTemplateRequest request) {
        log.info("POST /api/v1/admin/logistics/freight-templates: name={}", request.getName());
        FreightTemplate template = freightTemplateService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(template);
    }
}
