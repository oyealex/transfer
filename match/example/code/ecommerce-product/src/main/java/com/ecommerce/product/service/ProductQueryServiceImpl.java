package com.ecommerce.product.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.ProductSnapshotDto;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ProductQueryService} that provides product data
 * to other modules without exposing JPA entities.
 *
 * <p>Other modules (inventory, order, cart, promotion, etc.) depend on this
 * interface rather than directly accessing product repositories or entities.
 */
@Service
public class ProductQueryServiceImpl implements ProductQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProductQueryServiceImpl.class);

    private final ProductSkuRepository skuRepository;
    private final ObjectMapper objectMapper;

    public ProductQueryServiceImpl(ProductSkuRepository skuRepository, ObjectMapper objectMapper) {
        this.skuRepository = skuRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public SkuDto getSku(Long skuId) {
        ProductSku sku = skuRepository.findById(skuId).orElse(null);
        if (sku == null) {
            return null;
        }
        return toSkuDto(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public SkuDto getSkuForSale(Long skuId) {
        ProductSku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductSku", skuId));

        if (sku.getStatus() != SkuStatus.ON_SHELF) {
            throw new BusinessException("SKU_NOT_AVAILABLE",
                    "SKU " + skuId + " is not available for sale, current status: " + sku.getStatus());
        }

        return toSkuDto(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkuDto> listSkuByIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return skuRepository.findByIdIn(skuIds).stream()
                .map(this::toSkuDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSnapshotDto getProductSnapshot(Long skuId) {
        ProductSku sku = skuRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductSku", skuId));

        ProductSnapshotDto snapshot = new ProductSnapshotDto();
        snapshot.setSkuId(sku.getId());
        snapshot.setName(sku.getName());
        snapshot.setPrice(sku.getPrice());
        snapshot.setImage(sku.getImage());
        snapshot.setSpecs(parseSpecs(sku.getSpecs()));
        return snapshot;
    }

    private SkuDto toSkuDto(ProductSku sku) {
        SkuDto dto = new SkuDto();
        dto.setSkuId(sku.getId());
        dto.setSpuId(sku.getSpuId());
        dto.setSkuCode(sku.getSkuCode());
        dto.setName(sku.getName());
        dto.setPrice(sku.getPrice());
        dto.setStatus(sku.getStatus().name());
        dto.setSpecs(parseSpecs(sku.getSpecs()));
        return dto;
    }

    private Map<String, String> parseSpecs(String specsJson) {
        if (specsJson == null || specsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(specsJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse specs JSON in ProductQueryServiceImpl: {}", specsJson, e);
            return Collections.emptyMap();
        }
    }
}
