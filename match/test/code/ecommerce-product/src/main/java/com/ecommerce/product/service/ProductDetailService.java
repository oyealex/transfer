package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.ProductDetailResponse;
import com.ecommerce.product.entity.Brand;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.repository.BrandRepository;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.ecommerce.product.repository.ProductSpuRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for assembling full product detail responses including SKU info,
 * SPU info, brand, category, specs, images, and stock summary.
 */
@Service
public class ProductDetailService {

    private static final Logger log = LoggerFactory.getLogger(ProductDetailService.class);

    private final ProductSkuRepository skuRepository;
    private final ProductSpuRepository spuRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    private final com.ecommerce.product.query.InventoryQueryService inventoryQueryService;

    public ProductDetailService(ProductSkuRepository skuRepository,
                                ProductSpuRepository spuRepository,
                                BrandRepository brandRepository,
                                CategoryRepository categoryRepository,
                                ObjectMapper objectMapper,
                                com.ecommerce.product.query.InventoryQueryService inventoryQueryService) {
        this.skuRepository = skuRepository;
        this.spuRepository = spuRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
        this.inventoryQueryService = inventoryQueryService;
    }

    /**
     * Returns the full product detail for a given SKU.
     */
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(Long skuId) {
        ProductSku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductSku", skuId));

        ProductSpu spu = spuRepository.findById(sku.getSpuId())
                .orElseThrow(() -> new ResourceNotFoundException("ProductSpu", sku.getSpuId()));

        ProductDetailResponse response = new ProductDetailResponse();
        response.setSkuId(sku.getId());
        response.setSpuId(sku.getSpuId());
        response.setName(sku.getName());
        response.setPrice(sku.getPrice());
        response.setStatus(sku.getStatus().name());

        response.setStockSummary(inventoryQueryService.getStockSummary(skuId));

        response.setSpuName(spu.getName());

        // Brand name
        if (spu.getBrandId() != null) {
            brandRepository.findById(spu.getBrandId())
                    .map(Brand::getName)
                    .ifPresent(response::setBrand);
        }

        // Category name
        if (spu.getCategoryId() != null) {
            categoryRepository.findById(spu.getCategoryId())
                    .map(Category::getName)
                    .ifPresent(response::setCategory);
        }

        // Parse specs from JSON
        response.setSpecs(parseSpecs(sku.getSpecs()));

        // Parse images from JSON
        response.setImages(parseImages(spu.getImages()));

        log.debug("Built product detail for skuId={}", skuId);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseSpecs(String specsJson) {
        if (specsJson == null || specsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(specsJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse specs JSON: {}", specsJson, e);
            return Collections.emptyMap();
        }
    }

    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse images JSON: {}", imagesJson, e);
            return Collections.emptyList();
        }
    }
}
