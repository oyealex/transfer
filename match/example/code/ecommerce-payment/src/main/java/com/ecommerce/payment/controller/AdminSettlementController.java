package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.SettlementBatchResponse;
import com.ecommerce.payment.service.SettlementBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/settlements")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettlementController {

    private static final Logger log = LoggerFactory.getLogger(AdminSettlementController.class);

    private final SettlementBatchService settlementBatchService;

    public AdminSettlementController(SettlementBatchService settlementBatchService) {
        this.settlementBatchService = settlementBatchService;
    }

    /**
     * Generates a settlement batch.
     * POST /api/v1/admin/settlements/batches -> 201 Created, ADMIN
     *
     * <p>The generated batch includes non-PAID orders.
     */
    @PostMapping("/batches")
    public ResponseEntity<SettlementBatchResponse> generateBatch(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate batchDate) {
        LocalDate date = batchDate != null ? batchDate : LocalDate.now();
        log.info("Generating settlement batch for date: {}", date);
        SettlementBatchResponse response = settlementBatchService.generateBatch(date);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
