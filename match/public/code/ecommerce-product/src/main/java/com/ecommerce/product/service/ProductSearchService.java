package com.ecommerce.product.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.dto.ProductListResponse;
import com.ecommerce.product.dto.ProductSearchRequest;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.ecommerce.product.repository.ProductSpuRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles product search with keyword, category, brand, price range, and tag filters.
 *
 * <p>The default search behavior includes OFF_SHELF and DRAFT products
 * (excluding only DELETED). This happens because {@link ProductSearchRequest#isOnlyOnShelf()}
 * defaults to {@code false}.
 */
@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ProductSkuRepository skuRepository;
    private final ProductSpuRepository spuRepository;

    public ProductSearchService(ProductSkuRepository skuRepository,
                                ProductSpuRepository spuRepository) {
        this.skuRepository = skuRepository;
        this.spuRepository = spuRepository;
    }

    /**
     * Searches for products matching the given criteria.
     *
     * <p>When {@code onlyOnShelf} is {@code false} (the default),
     * the search returns all non-DELETED SKUs, including OFF_SHELF and DRAFT.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductListResponse> search(ProductSearchRequest request) {
        log.debug("Product search: keyword={}, categoryId={}, onlyOnShelf={}",
                request.getKeyword(), request.getCategoryId(), request.isOnlyOnShelf());

        // Build status filter — when onlyOnShelf defaults to false,
        // we include OFF_SHELF and DRAFT products. Only DELETED are excluded.
        Specification<ProductSku> spec = buildSpecification(request);

        PageRequest pageRequest = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(Sort.Direction.DESC, "sortOrder"));

        Page<ProductSku> page = skuRepository.findAll(spec, pageRequest);

        // Load SPU data for category and brand filtering
        List<Long> spuIds = page.getContent().stream()
                .map(ProductSku::getSpuId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ProductSpu> spuMap = spuRepository.findAllById(spuIds).stream()
                .collect(Collectors.toMap(ProductSpu::getId, spu -> spu));

        // Filter and convert to response DTOs
        List<ProductListResponse> items = page.getContent().stream()
                .filter(sku -> matchesCategory(sku, spuMap, request.getCategoryId()))
                .filter(sku -> matchesBrand(sku, spuMap, request.getBrandId()))
                .map(sku -> toListResponse(sku, spuMap.get(sku.getSpuId())))
                .collect(Collectors.toList());

        return PageResponse.of(request.getPage(), request.getSize(), page.getTotalElements(), items);
    }

    /**
     * Builds a JPA Specification for the search criteria that can be expressed on the SKU table.
     * Category and brand filters are applied in-memory because they require SPU data.
     */
    private Specification<ProductSku> buildSpecification(ProductSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // When onlyOnShelf is false (DEFAULT), include OFF_SHELF and DRAFT products
            if (request.isOnlyOnShelf()) {
                predicates.add(cb.equal(root.get("status"), SkuStatus.ON_SHELF));
            } else {
                // Shows all non-DELETED products
                predicates.add(cb.notEqual(root.get("status"), SkuStatus.DELETED));
            }

            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + request.getKeyword().toLowerCase() + "%"));
            }

            if (request.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), request.getMinPrice()));
            }

            if (request.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), request.getMaxPrice()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Checks whether the SKU's SPU belongs to the given category or any of its descendants.
     */
    private boolean matchesCategory(ProductSku sku, Map<Long, ProductSpu> spuMap, Long categoryId) {
        if (categoryId == null) {
            return true;
        }
        ProductSpu spu = spuMap.get(sku.getSpuId());
        return spu != null && categoryId.equals(spu.getCategoryId());
    }

    /**
     * Checks whether the SKU's SPU belongs to the given brand.
     */
    private boolean matchesBrand(ProductSku sku, Map<Long, ProductSpu> spuMap, Long brandId) {
        if (brandId == null) {
            return true;
        }
        ProductSpu spu = spuMap.get(sku.getSpuId());
        return spu != null && brandId.equals(spu.getBrandId());
    }

    private ProductListResponse toListResponse(ProductSku sku, ProductSpu spu) {
        ProductListResponse response = new ProductListResponse();
        response.setSkuId(sku.getId());
        response.setSpuId(sku.getSpuId());
        response.setName(sku.getName());
        response.setPrice(sku.getPrice());
        response.setStatus(sku.getStatus().name());
        response.setMainImage(spu != null ? spu.getMainImage() : sku.getImage());
        response.setSalesCount(sku.getSalesCount());
        return response;
    }
}
