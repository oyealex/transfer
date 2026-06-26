package com.ecommerce.product.controller;

import com.ecommerce.product.dto.SkuCreateRequest;
import com.ecommerce.product.dto.SpuCreateRequest;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.service.SkuService;
import com.ecommerce.product.service.SpuService;
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
 * Admin-only controller for product management operations.
 * Requires ADMIN role for all endpoints.
 */
@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private static final Logger log = LoggerFactory.getLogger(AdminProductController.class);

    private final SpuService spuService;
    private final SkuService skuService;

    public AdminProductController(SpuService spuService, SkuService skuService) {
        this.spuService = spuService;
        this.skuService = skuService;
    }

    /**
     * Creates a new SPU.
     */
    @PostMapping("/spu")
    public ResponseEntity<ProductSpu> createSpu(@Valid @RequestBody SpuCreateRequest request) {
        log.info("Admin creating SPU: spuCode={}", request.getSpuCode());
        ProductSpu spu = spuService.createSpu(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(spu);
    }

    /**
     * Creates a new SKU under an existing SPU.
     */
    @PostMapping("/sku")
    public ResponseEntity<ProductSku> createSku(@Valid @RequestBody SkuCreateRequest request) {
        log.info("Admin creating SKU: skuCode={}, spuId={}", request.getSkuCode(), request.getSpuId());
        ProductSku sku = skuService.createSku(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(sku);
    }

    /**
     * Puts a SKU on shelf, making it available for sale.
     */
    @PostMapping("/sku/{skuId}/on-shelf")
    public ResponseEntity<Void> onShelf(@PathVariable Long skuId) {
        log.info("Admin putting SKU on shelf: skuId={}", skuId);
        skuService.onShelf(skuId);
        return ResponseEntity.ok().build();
    }

    /**
     * Takes a SKU off shelf, making it unavailable for sale.
     */
    @PostMapping("/sku/{skuId}/off-shelf")
    public ResponseEntity<Void> offShelf(@PathVariable Long skuId) {
        log.info("Admin taking SKU off shelf: skuId={}", skuId);
        skuService.offShelf(skuId);
        return ResponseEntity.ok().build();
    }
}
