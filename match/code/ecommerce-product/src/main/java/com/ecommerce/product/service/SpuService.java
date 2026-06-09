package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.product.dto.SpuCreateRequest;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.repository.ProductSpuRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing SPU (Standard Product Unit) operations.
 */
@Service
public class SpuService {

    private static final Logger log = LoggerFactory.getLogger(SpuService.class);

    private final ProductSpuRepository spuRepository;
    private final ObjectMapper objectMapper;

    public SpuService(ProductSpuRepository spuRepository, ObjectMapper objectMapper) {
        this.spuRepository = spuRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new SPU from the given request.
     */
    @Transactional
    public ProductSpu createSpu(SpuCreateRequest request) {
        if (spuRepository.findBySpuCode(request.getSpuCode()).isPresent()) {
            throw new ValidationException("spuCode", "SPU code already exists: " + request.getSpuCode());
        }

        ProductSpu spu = new ProductSpu();
        spu.setSpuCode(request.getSpuCode());
        spu.setName(request.getName());
        spu.setDescription(request.getDescription());
        spu.setBrandId(request.getBrandId());
        spu.setCategoryId(request.getCategoryId());
        spu.setMainImage(request.getMainImage());

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            try {
                spu.setImages(objectMapper.writeValueAsString(request.getImages()));
            } catch (JsonProcessingException e) {
                throw new ValidationException("images", "Failed to serialize images list");
            }
        }

        spu.setStatus("DRAFT");

        ProductSpu saved = spuRepository.save(spu);
        log.info("Created SPU: id={}, spuCode={}", saved.getId(), saved.getSpuCode());
        return saved;
    }

    /**
     * Retrieves a SPU by its id.
     */
    @Transactional(readOnly = true)
    public ProductSpu getSpu(Long id) {
        return spuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductSpu", id));
    }
}
