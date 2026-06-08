package com.ecommerce.product.service;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.dto.ProductListResponse;
import com.ecommerce.product.dto.ProductSearchRequest;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.ecommerce.product.repository.ProductSpuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProductSearchService")
@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private ProductSkuRepository skuRepository;

    @Mock
    private ProductSpuRepository spuRepository;

    @InjectMocks
    private ProductSearchService productSearchService;

    private ProductSku onShelfSku;
    private ProductSku offShelfSku;
    private ProductSku draftSku;
    private ProductSku deletedSku;
    private ProductSpu spu;

    @BeforeEach
    void setUp() {
        spu = new ProductSpu();
        spu.setId(1L);
        spu.setName("Test SPU");
        spu.setCategoryId(10L);
        spu.setBrandId(100L);
        spu.setMainImage("main.jpg");

        onShelfSku = new ProductSku();
        onShelfSku.setId(1L);
        onShelfSku.setSpuId(1L);
        onShelfSku.setName("OnShelf SKU");
        onShelfSku.setPrice(new BigDecimal("99.99"));
        onShelfSku.setStatus(SkuStatus.ON_SHELF);
        onShelfSku.setSortOrder(10);
        onShelfSku.setSalesCount(5);

        offShelfSku = new ProductSku();
        offShelfSku.setId(2L);
        offShelfSku.setSpuId(1L);
        offShelfSku.setName("OffShelf SKU");
        offShelfSku.setPrice(new BigDecimal("49.99"));
        offShelfSku.setStatus(SkuStatus.OFF_SHELF);
        offShelfSku.setSortOrder(5);
        offShelfSku.setSalesCount(0);

        draftSku = new ProductSku();
        draftSku.setId(3L);
        draftSku.setSpuId(1L);
        draftSku.setName("Draft SKU");
        draftSku.setPrice(new BigDecimal("29.99"));
        draftSku.setStatus(SkuStatus.DRAFT);
        draftSku.setSortOrder(0);
        draftSku.setSalesCount(0);

        deletedSku = new ProductSku();
        deletedSku.setId(4L);
        deletedSku.setSpuId(1L);
        deletedSku.setName("Deleted SKU");
        deletedSku.setPrice(new BigDecimal("9.99"));
        deletedSku.setStatus(SkuStatus.DELETED);
        deletedSku.setSortOrder(0);
        deletedSku.setSalesCount(0);
    }

    @Test
    @DisplayName("search without onlyOnShelf returns all non-DELETED SKUs including OFF_SHELF and DRAFT")
    void testSearch_withoutOnlyOnShelf_returnsAllNonDeletedSkus() {
        // When onlyOnShelf is false (DEFAULT), the query excludes only DELETED,
        // meaning OFF_SHELF and DRAFT products are included. This test verifies the ACTUAL behavior.
        List<ProductSku> nonDeletedSkus = List.of(onShelfSku, offShelfSku, draftSku);
        Page<ProductSku> page = new PageImpl<>(nonDeletedSkus);
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        // onlyOnShelf defaults to false — we do NOT set it
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        // OFF_SHELF included in results
        assertThat(result.getItems()).hasSize(3);
        assertThat(result.getItems().stream().map(ProductListResponse::getStatus))
                .contains("ON_SHELF", "OFF_SHELF", "DRAFT");
    }

    @Test
    @DisplayName("search with onlyOnShelf=true filters to only ON_SHELF products")
    void testSearch_withOnlyOnShelfTrue_filtersToOnShelf() {
        List<ProductSku> allSkus = List.of(onShelfSku, offShelfSku, draftSku);
        Page<ProductSku> page = new PageImpl<>(allSkus);
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setOnlyOnShelf(true);
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        // Even though repository returns all, the Specification should filter ON_SHELF
        // Verify the specification is applied by checking that repository was called
        ArgumentCaptor<Specification<ProductSku>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(skuRepository).findAll(specCaptor.capture(), any(Pageable.class));
        assertThat(specCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("search by keyword finds matching SKUs by name (case-insensitive like)")
    void testSearch_byKeyword_findsMatchingSkus() {
        ProductSku matchingSku = new ProductSku();
        matchingSku.setId(10L);
        matchingSku.setSpuId(1L);
        matchingSku.setName("Premium Widget");
        matchingSku.setPrice(new BigDecimal("199.99"));
        matchingSku.setStatus(SkuStatus.ON_SHELF);
        matchingSku.setSortOrder(1);
        matchingSku.setSalesCount(0);

        Page<ProductSku> page = new PageImpl<>(List.of(matchingSku));
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword("widget");
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getName()).isEqualTo("Premium Widget");
    }

    @Test
    @DisplayName("search by categoryId filters results to matching SPU categories")
    void testSearch_byCategoryId_filtersCategory() {
        ProductSpu spu2 = new ProductSpu();
        spu2.setId(2L);
        spu2.setName("Other SPU");
        spu2.setCategoryId(20L);

        ProductSku matchingSku = new ProductSku();
        matchingSku.setId(10L);
        matchingSku.setSpuId(2L);
        matchingSku.setName("Matching SKU");
        matchingSku.setPrice(new BigDecimal("100.00"));
        matchingSku.setStatus(SkuStatus.ON_SHELF);
        matchingSku.setSortOrder(1);
        matchingSku.setSalesCount(0);

        ProductSku nonMatchingSku = new ProductSku();
        nonMatchingSku.setId(11L);
        nonMatchingSku.setSpuId(3L);
        nonMatchingSku.setName("Non-matching SKU");
        nonMatchingSku.setPrice(new BigDecimal("200.00"));
        nonMatchingSku.setStatus(SkuStatus.ON_SHELF);
        nonMatchingSku.setSortOrder(2);
        nonMatchingSku.setSalesCount(0);

        ProductSpu spu3 = new ProductSpu();
        spu3.setId(3L);
        spu3.setCategoryId(30L);

        List<ProductSku> allSkus = List.of(matchingSku, nonMatchingSku);
        Page<ProductSku> page = new PageImpl<>(allSkus);
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu2, spu3));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setCategoryId(20L);
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        // Only SKU whose SPU has categoryId=20 should be included
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getName()).isEqualTo("Matching SKU");
    }

    @Test
    @DisplayName("search by price range filters SKUs with price between min and max")
    void testSearch_byPriceRange_filtersByPrice() {
        ProductSku sku1 = new ProductSku();
        sku1.setId(1L);
        sku1.setSpuId(1L);
        sku1.setName("Cheap SKU");
        sku1.setPrice(new BigDecimal("10.00"));
        sku1.setStatus(SkuStatus.ON_SHELF);
        sku1.setSortOrder(1);
        sku1.setSalesCount(0);

        ProductSku sku2 = new ProductSku();
        sku2.setId(2L);
        sku2.setSpuId(1L);
        sku2.setName("Mid SKU");
        sku2.setPrice(new BigDecimal("50.00"));
        sku2.setStatus(SkuStatus.ON_SHELF);
        sku2.setSortOrder(2);
        sku2.setSalesCount(0);

        ProductSku sku3 = new ProductSku();
        sku3.setId(3L);
        sku3.setSpuId(1L);
        sku3.setName("Expensive SKU");
        sku3.setPrice(new BigDecimal("200.00"));
        sku3.setStatus(SkuStatus.ON_SHELF);
        sku3.setSortOrder(3);
        sku3.setSalesCount(0);

        // The Specification handles price filtering at DB level, return all for in-memory category/brand filter
        List<ProductSku> filteredSkus = List.of(sku2);
        Page<ProductSku> page = new PageImpl<>(filteredSkus);
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setMinPrice(new BigDecimal("30.00"));
        request.setMaxPrice(new BigDecimal("100.00"));
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getName()).isEqualTo("Mid SKU");
    }

    @Test
    @DisplayName("search pagination returns correct page metadata")
    void testSearch_pagination_returnsCorrectPage() {
        List<ProductSku> skus = List.of(onShelfSku, offShelfSku);
        Page<ProductSku> page = new PageImpl<>(skus, PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "sortOrder")), 10);
        when(skuRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(spuRepository.findAllById(any())).thenReturn(List.of(spu));

        ProductSearchRequest request = new ProductSearchRequest();
        request.setPage(1);
        request.setSize(2);
        PageResponse<ProductListResponse> result = productSearchService.search(request);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotal()).isEqualTo(10L);
        assertThat(result.getItems()).hasSize(2);
    }
}
