package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.ProductDetailResponse;
import com.ecommerce.product.entity.Brand;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.query.StockSummaryDto;
import com.ecommerce.product.repository.BrandRepository;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductSkuRepository;
import com.ecommerce.product.repository.ProductSpuRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProductDetailService")
@ExtendWith(MockitoExtension.class)
class ProductDetailServiceTest {

    @Mock
    private ProductSkuRepository skuRepository;

    @Mock
    private ProductSpuRepository spuRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StockInfoFetcher stockInfoFetcher;

    @InjectMocks
    private ProductDetailService productDetailService;

    private ProductSku sku;
    private ProductSpu spu;
    private Brand brand;
    private Category category;
    private StockSummaryDto stockSummary;

    @BeforeEach
    void setUp() {
        sku = new ProductSku();
        sku.setId(1L);
        sku.setSpuId(10L);
        sku.setName("Test SKU");
        sku.setPrice(new BigDecimal("99.99"));
        sku.setStatus(SkuStatus.ON_SHELF);
        sku.setSpecs("{\"color\":\"red\",\"size\":\"L\"}");

        spu = new ProductSpu();
        spu.setId(10L);
        spu.setName("Test SPU");
        spu.setBrandId(100L);
        spu.setCategoryId(200L);
        spu.setImages("[\"img1.jpg\",\"img2.jpg\"]");

        brand = new Brand();
        brand.setId(100L);
        brand.setName("Test Brand");

        category = new Category();
        category.setId(200L);
        category.setName("Test Category");

        stockSummary = new StockSummaryDto(999, 0);
    }

    @Test
    @DisplayName("getProductDetail returns SKU with stock summary from StockInfoFetcher")
    void testGetProductDetail_returnsSkuWithStockSummary() throws JsonProcessingException {
        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku));
        when(spuRepository.findById(10L)).thenReturn(Optional.of(spu));
        when(stockInfoFetcher.fetch(1L)).thenReturn(stockSummary);
        when(brandRepository.findById(100L)).thenReturn(Optional.of(brand));
        when(categoryRepository.findById(200L)).thenReturn(Optional.of(category));
        when(objectMapper.readValue(eq("{\"color\":\"red\",\"size\":\"L\"}"), any(TypeReference.class)))
                .thenReturn(Map.of("color", "red", "size", "L"));
        when(objectMapper.readValue(eq("[\"img1.jpg\",\"img2.jpg\"]"), any(TypeReference.class)))
                .thenReturn(List.of("img1.jpg", "img2.jpg"));

        ProductDetailResponse result = productDetailService.getProductDetail(1L);

        assertThat(result.getSkuId()).isEqualTo(1L);
        assertThat(result.getSpuId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Test SKU");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(result.getStatus()).isEqualTo("ON_SHELF");
        assertThat(result.getSpuName()).isEqualTo("Test SPU");
        assertThat(result.getBrand()).isEqualTo("Test Brand");
        assertThat(result.getCategory()).isEqualTo("Test Category");
        // Stock summary comes from StockInfoFetcher instead of InventoryQueryService
        assertThat(result.getStockSummary().getAvailableStock()).isEqualTo(999);
        assertThat(result.getStockSummary().getReservedStock()).isZero();

        // Verify StockInfoFetcher was used
        verify(stockInfoFetcher).fetch(1L);
    }

    @Test
    @DisplayName("getProductDetail throws ResourceNotFoundException when SKU not found")
    void testGetProductDetail_throwsWhenSkuNotFound() {
        when(skuRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productDetailService.getProductDetail(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProductDetail throws ResourceNotFoundException when SPU not found")
    void testGetProductDetail_throwsWhenSpuNotFound() {
        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku));
        when(spuRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productDetailService.getProductDetail(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProductDetail handles null brand and category gracefully")
    void testGetProductDetail_handlesNullBrandAndCategory() throws JsonProcessingException {
        spu.setBrandId(null);
        spu.setCategoryId(null);

        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku));
        when(spuRepository.findById(10L)).thenReturn(Optional.of(spu));
        when(stockInfoFetcher.fetch(1L)).thenReturn(stockSummary);
        when(objectMapper.readValue(eq("{\"color\":\"red\",\"size\":\"L\"}"), any(TypeReference.class)))
                .thenReturn(Map.of("color", "red", "size", "L"));
        when(objectMapper.readValue(eq("[\"img1.jpg\",\"img2.jpg\"]"), any(TypeReference.class)))
                .thenReturn(List.of("img1.jpg", "img2.jpg"));

        ProductDetailResponse result = productDetailService.getProductDetail(1L);

        assertThat(result.getBrand()).isNull();
        assertThat(result.getCategory()).isNull();
    }

    @Test
    @DisplayName("getProductDetail handles empty specs and images gracefully")
    void testGetProductDetail_handlesEmptySpecsAndImages() {
        sku.setSpecs(null);
        spu.setImages(null);

        when(skuRepository.findById(1L)).thenReturn(Optional.of(sku));
        when(spuRepository.findById(10L)).thenReturn(Optional.of(spu));
        when(stockInfoFetcher.fetch(1L)).thenReturn(stockSummary);
        when(brandRepository.findById(100L)).thenReturn(Optional.of(brand));
        when(categoryRepository.findById(200L)).thenReturn(Optional.of(category));

        ProductDetailResponse result = productDetailService.getProductDetail(1L);

        assertThat(result.getSpecs()).isEmpty();
        assertThat(result.getImages()).isEmpty();
    }
}
