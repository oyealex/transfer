package com.ecommerce.app.controller;

import com.ecommerce.common.test.FaultInjectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ops")
public class FaultInjectionAdminController {

    private static final Logger log = LoggerFactory.getLogger(FaultInjectionAdminController.class);

    @PostMapping("/fault-injections")
    public ResponseEntity<Map<String, Object>> injectFault(@RequestBody Map<String, String> body) {
        String faultName = body.get("fault");
        if (faultName == null || faultName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fault name is required"));
        }
        FaultInjectionRegistry.add(faultName);
        log.info("Fault injected: {} (active faults: {})", faultName, FaultInjectionRegistry.getAll());
        return ResponseEntity.ok(Map.of("activeFaults", FaultInjectionRegistry.getAll()));
    }

    @DeleteMapping("/fault-injections")
    public ResponseEntity<Void> clearFaults() {
        FaultInjectionRegistry.clear();
        log.info("All faults cleared");
        return ResponseEntity.noContent().build();
    }
}
