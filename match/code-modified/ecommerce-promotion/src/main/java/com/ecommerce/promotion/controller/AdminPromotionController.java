package com.ecommerce.promotion.controller;

import com.ecommerce.promotion.dto.CouponCreateRequest;
import com.ecommerce.promotion.dto.FullReductionCreateRequest;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.FullReductionActivity;
import com.ecommerce.promotion.entity.SeckillActivity;
import com.ecommerce.promotion.service.CouponTemplateService;
import com.ecommerce.promotion.service.FullReductionService;
import com.ecommerce.promotion.service.SeckillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only promotion management endpoints.
 * Requires ADMIN role (enforced at gateway or security layer).
 */
@RestController
@RequestMapping("/api/v1/admin/promotions")
public class AdminPromotionController {

    private final CouponTemplateService couponTemplateService;
    private final FullReductionService fullReductionService;
    private final SeckillService seckillService;

    public AdminPromotionController(CouponTemplateService couponTemplateService,
                                     FullReductionService fullReductionService,
                                     SeckillService seckillService) {
        this.couponTemplateService = couponTemplateService;
        this.fullReductionService = fullReductionService;
        this.seckillService = seckillService;
    }

    /**
     * Create a new coupon template.
     * POST /api/v1/admin/promotions/coupons → 201 Created
     */
    @PostMapping("/coupons")
    public ResponseEntity<CouponTemplate> createCoupon(@Valid @RequestBody CouponCreateRequest request) {
        CouponTemplate created = couponTemplateService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Create a new full-reduction activity.
     * POST /api/v1/admin/promotions/full-reductions → 201 Created
     */
    @PostMapping("/full-reductions")
    public ResponseEntity<FullReductionActivity> createFullReduction(
            @Valid @RequestBody FullReductionCreateRequest request) {
        FullReductionActivity created = fullReductionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Create a new seckill activity.
     * POST /api/v1/admin/promotions/seckill → 201 Created
     */
    @PostMapping("/seckill")
    public ResponseEntity<SeckillActivity> createSeckill(@Valid @RequestBody SeckillActivity activity) {
        SeckillActivity created = seckillService.create(activity);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
