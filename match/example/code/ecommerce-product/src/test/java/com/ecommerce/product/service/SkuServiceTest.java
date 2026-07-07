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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SkuService")
@ExtendWith(MockitoExtension.class)
class SkuServiceTest {

    @Mock
    private ProductSkuRepository skuRepository;

    @Mock
    private ProductSpuRepository spuRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SkuService skuService;

    private SkuCreateRequest createRequest;
    private ProductSku savedSku;

    @BeforeEach
    void setUp() {
        createRequest = new SkuCreateRequest();
        createRequest.setSpuId(1L);
        createRequest.setSkuCode("SKU-001");
        createRequest.setName("Test SKU");
        createRequest.setPrice(new BigDecimal("99.99"));
        createRequest.setMarketPrice(new BigDecimal("129.99"));
        createRequest.setImage("sku.jpg");

        savedSku = new ProductSku();
        savedSku.setId(1L);
        savedSku.setSpuId(1L);
        savedSku.setSkuCode("SKU-001");
        savedSku.setName("Test SKU");
        savedSku.setPrice(new BigDecimal("99.99"));
        savedSku.setMarketPrice(new BigDecimal("129.99"));
        savedSku.setImage("sku.jpg");
        savedSku.setStatus(SkuStatus.DRAFT);
        savedSku.setSortOrder(0);
        savedSku.setSalesCount(0);
    }

    @Test
    @DisplayName("createSku saves SKU with DRAFT status")
    void testCreateSku_savesWithDraftStatus() {
        when(spuRepository.existsById(1L)).thenReturn(true);
        when(skuRepository.findBySkuCode("SKU-001")).thenReturn(Optional.empty());
        when(skuRepository.save(any(ProductSku.class))).thenReturn(savedSku);

        ProductSku result = skuService.createSku(createRequest);

        assertThat(result.getStatus()).isEqualTo(SkuStatus.DRAFT);
        assertThat(result.getSkuCode()).isEqualTo("SKU-001");
        assertThat(result.getName()).isEqualTo("Test SKU");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(result.getSalesCount()).isZero();
        assertThat(result.getSortOrder()).isZero();
        verify(skuRepository).save(any(ProductSku.class));
    }

    @Test
    @DisplayName("createSku throws ResourceNotFoundException when SPU does not exist")
    void testCreateSku_throwsWhenSpuNotFound() {
        when(spuRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> skuService.createSku(createRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("createSku throws ValidationException when skuCode already exists")
    void testCreateSku_throwsWhenSkuCodeDuplicate() {
        when(spuRepository.existsById(1L)).thenReturn(true);
        when(skuRepository.findBySkuCode("SKU-001")).thenReturn(Optional.of(savedSku));

        assertThatThrownBy(() -> skuService.createSku(createRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("SKU code already exists");
    }

    @Test
    @DisplayName("createSku serializes specs map to JSON")
    void testCreateSku_serializesSpecsToJson() throws JsonProcessingException {
        Map<String, String> specs = Map.of("color", "red", "size", "L");
        createRequest.setSpecs(specs);
        when(spuRepository.existsById(1L)).thenReturn(true);
        when(skuRepository.findBySkuCode("SKU-001")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(specs)).thenReturn("{\"color\":\"red\",\"size\":\"L\"}");
        when(skuRepository.save(any(ProductSku.class))).thenReturn(savedSku);

        skuService.createSku(createRequest);

        verify(objectMapper).writeValueAsString(specs);
    }

    @Test
    @DisplayName("onShelf changes SKU status from DRAFT to ON_SHELF")
    void testOnShelf_changesStatusToOnShelf() {
        ProductSku draftSku = new ProductSku();
        draftSku.setId(1L);
        draftSku.setSkuCode("SKU-001");
        draftSku.setStatus(SkuStatus.DRAFT);

        when(skuRepository.findById(1L)).thenReturn(Optional.of(draftSku));
        when(skuRepository.save(any(ProductSku.class))).thenReturn(draftSku);

        skuService.onShelf(1L);

        assertThat(draftSku.getStatus()).isEqualTo(SkuStatus.ON_SHELF);
        verify(skuRepository).save(draftSku);
    }

    @Test
    @DisplayName("onShelf changes SKU status from OFF_SHELF to ON_SHELF")
    void testOnShelf_changesStatusFromOffShelfToOnShelf() {
        ProductSku offShelfSku = new ProductSku();
        offShelfSku.setId(2L);
        offShelfSku.setSkuCode("SKU-002");
        offShelfSku.setStatus(SkuStatus.OFF_SHELF);

        when(skuRepository.findById(2L)).thenReturn(Optional.of(offShelfSku));
        when(skuRepository.save(any(ProductSku.class))).thenReturn(offShelfSku);

        skuService.onShelf(2L);

        assertThat(offShelfSku.getStatus()).isEqualTo(SkuStatus.ON_SHELF);
    }

    @Test
    @DisplayName("onShelf throws ValidationException when SKU is DELETED")
    void testOnShelf_throwsWhenSkuDeleted() {
        ProductSku deletedSku = new ProductSku();
        deletedSku.setId(3L);
        deletedSku.setStatus(SkuStatus.DELETED);

        when(skuRepository.findById(3L)).thenReturn(Optional.of(deletedSku));

        assertThatThrownBy(() -> skuService.onShelf(3L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot put a DELETED SKU on shelf");
    }

    @Test
    @DisplayName("offShelf changes SKU status from ON_SHELF to OFF_SHELF")
    void testOffShelf_changesStatusToOffShelf() {
        ProductSku onShelfSku = new ProductSku();
        onShelfSku.setId(1L);
        onShelfSku.setSkuCode("SKU-001");
        onShelfSku.setStatus(SkuStatus.ON_SHELF);

        when(skuRepository.findById(1L)).thenReturn(Optional.of(onShelfSku));
        when(skuRepository.save(any(ProductSku.class))).thenReturn(onShelfSku);

        skuService.offShelf(1L);

        assertThat(onShelfSku.getStatus()).isEqualTo(SkuStatus.OFF_SHELF);
        verify(skuRepository).save(onShelfSku);
    }

    @Test
    @DisplayName("offShelf throws ValidationException when SKU is DELETED")
    void testOffShelf_throwsWhenSkuDeleted() {
        ProductSku deletedSku = new ProductSku();
        deletedSku.setId(3L);
        deletedSku.setStatus(SkuStatus.DELETED);

        when(skuRepository.findById(3L)).thenReturn(Optional.of(deletedSku));

        assertThatThrownBy(() -> skuService.offShelf(3L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot take a DELETED SKU off shelf");
    }
}
