package com.ecommerce.loyalty.controller;

import com.ecommerce.loyalty.service.PointsExpireService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-facing loyalty endpoints under /api/v1/admin/loyalty.
 * Requires ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/loyalty")
public class AdminLoyaltyController {

    private static final Logger log = LoggerFactory.getLogger(AdminLoyaltyController.class);

    private final PointsExpireService pointsExpireService;

    public AdminLoyaltyController(PointsExpireService pointsExpireService) {
        this.pointsExpireService = pointsExpireService;
    }

    /**
     * Trigger manual expiration of points.
     */
    @PostMapping("/points/expire")
    public ResponseEntity<Map<String, Object>> expirePoints() {
        log.info("Admin requested points expiration");
        pointsExpireService.expire();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Points expiration processed"
        ));
    }
}
