package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.product.dto.SpuCreateRequest;
import com.ecommerce.product.entity.ProductSpu;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SpuService")
@ExtendWith(MockitoExtension.class)
class SpuServiceTest {

    @Mock
    private ProductSpuRepository spuRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SpuService spuService;

    private SpuCreateRequest createRequest;
    private ProductSpu savedSpu;

    @BeforeEach
    void setUp() {
        createRequest = new SpuCreateRequest();
        createRequest.setSpuCode("SPU-001");
        createRequest.setName("Test SPU");
        createRequest.setDescription("A test product description");
        createRequest.setBrandId(100L);
        createRequest.setCategoryId(200L);
        createRequest.setMainImage("main.jpg");

        savedSpu = new ProductSpu();
        savedSpu.setId(1L);
        savedSpu.setSpuCode("SPU-001");
        savedSpu.setName("Test SPU");
        savedSpu.setDescription("A test product description");
        savedSpu.setBrandId(100L);
        savedSpu.setCategoryId(200L);
        savedSpu.setMainImage("main.jpg");
        savedSpu.setStatus("DRAFT");
    }

    @Test
    @DisplayName("createSpu saves SPU with DRAFT status")
    void testCreateSpu_savesWithDraftStatus() {
        when(spuRepository.findBySpuCode("SPU-001")).thenReturn(Optional.empty());
        when(spuRepository.save(any(ProductSpu.class))).thenReturn(savedSpu);

        ProductSpu result = spuService.createSpu(createRequest);

        assertThat(result.getSpuCode()).isEqualTo("SPU-001");
        assertThat(result.getName()).isEqualTo("Test SPU");
        assertThat(result.getDescription()).isEqualTo("A test product description");
        assertThat(result.getBrandId()).isEqualTo(100L);
        assertThat(result.getCategoryId()).isEqualTo(200L);
        assertThat(result.getMainImage()).isEqualTo("main.jpg");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(spuRepository).save(any(ProductSpu.class));
    }

    @Test
    @DisplayName("createSpu throws ValidationException when spuCode already exists")
    void testCreateSpu_throwsWhenSpuCodeDuplicate() {
        when(spuRepository.findBySpuCode("SPU-001")).thenReturn(Optional.of(savedSpu));

        assertThatThrownBy(() -> spuService.createSpu(createRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("SPU code already exists");
    }

    @Test
    @DisplayName("createSpu serializes images list to JSON")
    void testCreateSpu_serializesImagesToJson() throws JsonProcessingException {
        List<String> images = List.of("img1.jpg", "img2.jpg", "img3.jpg");
        createRequest.setImages(images);
        when(spuRepository.findBySpuCode("SPU-001")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(images)).thenReturn("[\"img1.jpg\",\"img2.jpg\",\"img3.jpg\"]");
        when(spuRepository.save(any(ProductSpu.class))).thenReturn(savedSpu);

        spuService.createSpu(createRequest);

        verify(objectMapper).writeValueAsString(images);
    }

    @Test
    @DisplayName("getSpu returns SPU by id")
    void testGetSpu_returnsSpuById() {
        when(spuRepository.findById(1L)).thenReturn(Optional.of(savedSpu));

        ProductSpu result = spuService.getSpu(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getSpuCode()).isEqualTo("SPU-001");
        assertThat(result.getName()).isEqualTo("Test SPU");
    }

    @Test
    @DisplayName("getSpu throws ResourceNotFoundException when SPU not found")
    void testGetSpu_throwsWhenNotFound() {
        when(spuRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spuService.getSpu(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
