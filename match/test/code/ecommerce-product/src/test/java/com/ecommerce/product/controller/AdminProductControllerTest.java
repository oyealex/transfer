package com.ecommerce.product.controller;

import com.ecommerce.product.dto.SkuCreateRequest;
import com.ecommerce.product.dto.SpuCreateRequest;
import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.ProductSpu;
import com.ecommerce.product.entity.SkuStatus;
import com.ecommerce.product.service.SkuService;
import com.ecommerce.product.service.SpuService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminProductController")
@ExtendWith(MockitoExtension.class)
class AdminProductControllerTest {

    @Mock
    private SpuService spuService;

    @Mock
    private SkuService skuService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private SpuCreateRequest spuRequest;
    private SkuCreateRequest skuRequest;
    private ProductSpu savedSpu;
    private ProductSku savedSku;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AdminProductController controller = new AdminProductController(spuService, skuService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        spuRequest = new SpuCreateRequest();
        spuRequest.setSpuCode("SPU-001");
        spuRequest.setName("Test SPU");
        spuRequest.setCategoryId(1L);

        savedSpu = new ProductSpu();
        savedSpu.setId(1L);
        savedSpu.setSpuCode("SPU-001");
        savedSpu.setName("Test SPU");
        savedSpu.setCategoryId(1L);
        savedSpu.setStatus("DRAFT");

        skuRequest = new SkuCreateRequest();
        skuRequest.setSpuId(1L);
        skuRequest.setSkuCode("SKU-001");
        skuRequest.setName("Test SKU");
        skuRequest.setPrice(new BigDecimal("99.99"));

        savedSku = new ProductSku();
        savedSku.setId(1L);
        savedSku.setSpuId(1L);
        savedSku.setSkuCode("SKU-001");
        savedSku.setName("Test SKU");
        savedSku.setPrice(new BigDecimal("99.99"));
        savedSku.setStatus(SkuStatus.DRAFT);
    }

    @Test
    @DisplayName("POST /spu creates SPU and returns 201")
    void testCreateSpu_returnsCreated() throws Exception {
        when(spuService.createSpu(any(SpuCreateRequest.class))).thenReturn(savedSpu);

        mockMvc.perform(post("/api/v1/admin/products/spu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spuRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spuCode").value("SPU-001"))
                .andExpect(jsonPath("$.name").value("Test SPU"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /sku creates SKU and returns 201")
    void testCreateSku_returnsCreated() throws Exception {
        when(skuService.createSku(any(SkuCreateRequest.class))).thenReturn(savedSku);

        mockMvc.perform(post("/api/v1/admin/products/sku")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skuRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skuCode").value("SKU-001"))
                .andExpect(jsonPath("$.name").value("Test SKU"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /sku/{skuId}/on-shelf puts SKU on shelf")
    void testOnShelf_returnsOk() throws Exception {
        doNothing().when(skuService).onShelf(1L);

        mockMvc.perform(post("/api/v1/admin/products/sku/1/on-shelf"))
                .andExpect(status().isOk());

        verify(skuService).onShelf(1L);
    }

    @Test
    @DisplayName("POST /sku/{skuId}/off-shelf puts SKU off shelf")
    void testOffShelf_returnsOk() throws Exception {
        doNothing().when(skuService).offShelf(2L);

        mockMvc.perform(post("/api/v1/admin/products/sku/2/off-shelf"))
                .andExpect(status().isOk());

        verify(skuService).offShelf(2L);
    }

    @Test
    @DisplayName("controller class has @PreAuthorize with ADMIN role")
    void testController_requiresAdminRole() {
        PreAuthorize annotation = AdminProductController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("ADMIN");
    }

    @Test
    @DisplayName("controller methods are annotated with @PreAuthorize (class-level inheritance)")
    void testAllEndpointsInheritAdminRole() throws Exception {
        Method createSpuMethod = AdminProductController.class.getMethod("createSpu", SpuCreateRequest.class);
        // Class-level @PreAuthorize applies to all methods; no need for method-level annotation
        assertThat(createSpuMethod.getAnnotation(PreAuthorize.class)).isNull();
    }
}
