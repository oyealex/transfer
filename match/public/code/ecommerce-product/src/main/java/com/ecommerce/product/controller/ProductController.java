package com.ecommerce.product.controller;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.dto.ProductDetailResponse;
import com.ecommerce.product.dto.ProductListResponse;
import com.ecommerce.product.dto.ProductSearchRequest;
import com.ecommerce.product.service.ProductDetailService;
import com.ecommerce.product.service.ProductSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (anonymous) product browsing controller.
 * Provides product listing, search, and detail endpoints.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductSearchService productSearchService;
    private final ProductDetailService productDetailService;

    public ProductController(ProductSearchService productSearchService,
                             ProductDetailService productDetailService) {
        this.productSearchService = productSearchService;
        this.productDetailService = productDetailService;
    }

    /**
     * Lists products (default view).
     * Uses the search service with default parameters for listing.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ProductListResponse>> listProducts(ProductSearchRequest request) {
        log.debug("Listing products: page={}, size={}", request.getPage(), request.getSize());
        PageResponse<ProductListResponse> result = productSearchService.search(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Searches for products by keyword, category, brand, price range, etc.
     */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<ProductListResponse>> searchProducts(ProductSearchRequest request) {
        log.debug("Searching products: keyword={}, onlyOnShelf={}", request.getKeyword(), request.isOnlyOnShelf());
        PageResponse<ProductListResponse> result = productSearchService.search(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns full product detail for a given SKU, including stock summary.
     */
    @GetMapping("/{skuId}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(@PathVariable Long skuId) {
        log.debug("Getting product detail: skuId={}", skuId);
        ProductDetailResponse detail = productDetailService.getProductDetail(skuId);
        return ResponseEntity.ok(detail);
    }
}
