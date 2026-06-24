package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.product.dto.SkuCreateRequest;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.ecommerce.product.repository.ProductSpuRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing SKU (Stock Keeping Unit) operations.
 */
@Service
public class SkuService {

    private static final Logger log = LoggerFactory.getLogger(SkuService.class);

    private final ProductSkuRepository skuRepository;
    private final ProductSpuRepository spuRepository;
    private final ObjectMapper objectMapper;

    public SkuService(ProductSkuRepository skuRepository,
                      ProductSpuRepository spuRepository,
                      ObjectMapper objectMapper) {
        this.skuRepository = skuRepository;
        this.spuRepository = spuRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new SKU under an existing SPU.
     */
    @Transactional
    public ProductSku createSku(SkuCreateRequest request) {
        if (!spuRepository.existsById(request.getSpuId())) {
            throw new ResourceNotFoundException("ProductSpu", request.getSpuId());
        }

        if (skuRepository.findBySkuCode(request.getSkuCode()).isPresent()) {
            throw new ValidationException("skuCode", "SKU code already exists: " + request.getSkuCode());
        }

        ProductSku sku = new ProductSku();
        sku.setSpuId(request.getSpuId());
        sku.setSkuCode(request.getSkuCode());
        sku.setName(request.getName());
        sku.setPrice(request.getPrice());
        sku.setMarketPrice(request.getMarketPrice());

        if (request.getSpecs() != null && !request.getSpecs().isEmpty()) {
            try {
                sku.setSpecs(objectMapper.writeValueAsString(request.getSpecs()));
            } catch (JsonProcessingException e) {
                throw new ValidationException("specs", "Failed to serialize specs map");
            }
        }

        sku.setImage(request.getImage());
        sku.setStatus(SkuStatus.DRAFT);
        sku.setSortOrder(0);
        sku.setSalesCount(0);

        ProductSku saved = skuRepository.save(sku);
        log.info("Created SKU: id={}, skuCode={}, spuId={}", saved.getId(), saved.getSkuCode(), saved.getSpuId());
        return saved;
    }

    /**
     * Puts a SKU on shelf, making it available for sale.
     */
    @Transactional
    public void onShelf(Long skuId) {
        ProductSku sku = findSku(skuId);
        if (sku.getStatus() == SkuStatus.DELETED) {
            throw new ValidationException("status", "Cannot put a DELETED SKU on shelf");
        }
        sku.setStatus(SkuStatus.ON_SHELF);
        skuRepository.save(sku);
        log.info("SKU on shelf: skuId={}, skuCode={}", skuId, sku.getSkuCode());
    }

    /**
     * Takes a SKU off shelf, making it unavailable for sale.
     */
    @Transactional
    public void offShelf(Long skuId) {
        ProductSku sku = findSku(skuId);
        if (sku.getStatus() == SkuStatus.DELETED) {
            throw new ValidationException("status", "Cannot take a DELETED SKU off shelf");
        }
        sku.setStatus(SkuStatus.OFF_SHELF);
        skuRepository.save(sku);
        log.info("SKU off shelf: skuId={}, skuCode={}", skuId, sku.getSkuCode());
    }

    private ProductSku findSku(Long skuId) {
        return skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductSku", skuId));
    }
}
