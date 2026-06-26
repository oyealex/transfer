package com.ecommerce.product.controller;

import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.product.dto.ProductDetailResponse;
import com.ecommerce.product.dto.ProductListResponse;
import com.ecommerce.product.dto.ProductSearchRequest;
import com.ecommerce.product.query.StockSummaryDto;
import com.ecommerce.product.service.ProductDetailService;
import com.ecommerce.product.service.ProductSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ProductController")
@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private ProductDetailService productDetailService;

    private MockMvc mockMvc;

    private ProductListResponse listItem1;
    private ProductListResponse listItem2;
    private ProductDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        ProductController controller = new ProductController(productSearchService, productDetailService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        listItem1 = new ProductListResponse();
        listItem1.setSkuId(1L);
        listItem1.setSpuId(10L);
        listItem1.setName("Product 1");
        listItem1.setPrice(new BigDecimal("99.99"));
        listItem1.setStatus("ON_SHELF");
        listItem1.setMainImage("img1.jpg");
        listItem1.setSalesCount(100);

        listItem2 = new ProductListResponse();
        listItem2.setSkuId(2L);
        listItem2.setSpuId(11L);
        listItem2.setName("Product 2");
        listItem2.setPrice(new BigDecimal("149.99"));
        listItem2.setStatus("ON_SHELF");
        listItem2.setMainImage("img2.jpg");
        listItem2.setSalesCount(50);

        detailResponse = new ProductDetailResponse();
        detailResponse.setSkuId(1L);
        detailResponse.setSpuId(10L);
        detailResponse.setName("Product 1");
        detailResponse.setPrice(new BigDecimal("99.99"));
        detailResponse.setStatus("ON_SHELF");
        detailResponse.setStockSummary(new StockSummaryDto(100, 5));
        detailResponse.setSpuName("Product One SPU");
        detailResponse.setBrand("Test Brand");
        detailResponse.setCategory("Test Category");
        detailResponse.setSpecs(Map.of("color", "red"));
        detailResponse.setImages(List.of("img1.jpg"));
    }

    @Test
    @DisplayName("GET /api/v1/products returns paginated product list (anonymous access)")
    void testListProducts_returnsPage() throws Exception {
        PageResponse<ProductListResponse> page = PageResponse.of(0, 20, 2,
                List.of(listItem1, listItem2));
        when(productSearchService.search(any(ProductSearchRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Product 1"))
                .andExpect(jsonPath("$.items[1].name").value("Product 2"));
    }

    @Test
    @DisplayName("GET /api/v1/products/search returns search results (anonymous)")
    void testSearchProducts_returnsSearchResults() throws Exception {
        PageResponse<ProductListResponse> page = PageResponse.of(0, 20, 1,
                List.of(listItem1));
        when(productSearchService.search(any(ProductSearchRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search")
                        .param("keyword", "Product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Product 1"));
    }

    @Test
    @DisplayName("GET /api/v1/products/search with category filter delegates to search service")
    void testSearchProducts_withCategoryFilter() throws Exception {
        PageResponse<ProductListResponse> page = PageResponse.of(0, 10, 0, List.of());
        when(productSearchService.search(any(ProductSearchRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search")
                        .param("categoryId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/products/search with price range delegates to search service")
    void testSearchProducts_withPriceRange() throws Exception {
        PageResponse<ProductListResponse> page = PageResponse.of(0, 20, 0, List.of());
        when(productSearchService.search(any(ProductSearchRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search")
                        .param("minPrice", "10.00")
                        .param("maxPrice", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/products/{skuId} returns product detail (anonymous)")
    void testGetProductDetail_returnsDetail() throws Exception {
        when(productDetailService.getProductDetail(1L)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuId").value(1))
                .andExpect(jsonPath("$.name").value("Product 1"))
                .andExpect(jsonPath("$.price").value(99.99))
                .andExpect(jsonPath("$.status").value("ON_SHELF"))
                .andExpect(jsonPath("$.spuName").value("Product One SPU"))
                .andExpect(jsonPath("$.brand").value("Test Brand"))
                .andExpect(jsonPath("$.category").value("Test Category"))
                .andExpect(jsonPath("$.stockSummary.availableStock").value(100))
                .andExpect(jsonPath("$.stockSummary.reservedStock").value(5))
                .andExpect(jsonPath("$.specs.color").value("red"))
                .andExpect(jsonPath("$.images[0]").value("img1.jpg"));
    }
}
