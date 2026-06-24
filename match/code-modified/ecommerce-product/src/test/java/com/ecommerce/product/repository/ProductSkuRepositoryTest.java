package com.ecommerce.product.repository;

import com.ecommerce.product.entity.ProductSku;
import com.ecommerce.product.entity.SkuStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("ProductSkuRepository")
class ProductSkuRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductSkuRepository skuRepository;

    private ProductSku onShelfSku;
    private ProductSku offShelfSku;
    private ProductSku draftSku;

    @BeforeEach
    void setUp() {
        onShelfSku = createSku("SKU-ON-001", "OnShelf Product", SkuStatus.ON_SHELF, 1L);
        offShelfSku = createSku("SKU-OFF-001", "OffShelf Product", SkuStatus.OFF_SHELF, 1L);
        draftSku = createSku("SKU-DRAFT-001", "Draft Product", SkuStatus.DRAFT, 1L);

        entityManager.persist(onShelfSku);
        entityManager.persist(offShelfSku);
        entityManager.persist(draftSku);
        entityManager.flush();
    }

    @Test
    @DisplayName("findByStatus returns SKUs matching the given status")
    void testFindByStatus_returnsMatchingSkus() {
        List<ProductSku> onShelfResults = skuRepository.findByStatus(SkuStatus.ON_SHELF);
        assertThat(onShelfResults).hasSize(1);
        assertThat(onShelfResults.get(0).getSkuCode()).isEqualTo("SKU-ON-001");
        assertThat(onShelfResults.get(0).getStatus()).isEqualTo(SkuStatus.ON_SHELF);

        List<ProductSku> offShelfResults = skuRepository.findByStatus(SkuStatus.OFF_SHELF);
        assertThat(offShelfResults).hasSize(1);
        assertThat(offShelfResults.get(0).getSkuCode()).isEqualTo("SKU-OFF-001");

        List<ProductSku> draftResults = skuRepository.findByStatus(SkuStatus.DRAFT);
        assertThat(draftResults).hasSize(1);
        assertThat(draftResults.get(0).getSkuCode()).isEqualTo("SKU-DRAFT-001");
    }

    @Test
    @DisplayName("findByStatus returns empty list when no SKUs have the given status")
    void testFindByStatus_returnsEmptyWhenNoMatch() {
        List<ProductSku> results = skuRepository.findByStatus(SkuStatus.DELETED);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("findBySpuId returns all SKUs belonging to a SPU")
    void testFindBySpuId_returnsAllSkusForSpu() {
        ProductSku anotherSpuSku = createSku("SKU-OTHER-001", "Other SPU SKU", SkuStatus.ON_SHELF, 2L);
        entityManager.persist(anotherSpuSku);
        entityManager.flush();

        List<ProductSku> spu1Results = skuRepository.findBySpuId(1L);
        assertThat(spu1Results).hasSize(3);
        assertThat(spu1Results).extracting(ProductSku::getSkuCode)
                .containsExactlyInAnyOrder("SKU-ON-001", "SKU-OFF-001", "SKU-DRAFT-001");

        List<ProductSku> spu2Results = skuRepository.findBySpuId(2L);
        assertThat(spu2Results).hasSize(1);
        assertThat(spu2Results.get(0).getSkuCode()).isEqualTo("SKU-OTHER-001");
    }

    @Test
    @DisplayName("findBySpuId returns empty list when no SKUs belong to the SPU")
    void testFindBySpuId_returnsEmptyWhenNoMatch() {
        List<ProductSku> results = skuRepository.findBySpuId(999L);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("findBySkuCode returns SKU by unique skuCode")
    void testFindBySkuCode_returnsSku() {
        var result = skuRepository.findBySkuCode("SKU-ON-001");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("OnShelf Product");
        assertThat(result.get().getStatus()).isEqualTo(SkuStatus.ON_SHELF);
    }

    @Test
    @DisplayName("findBySkuCode returns empty when skuCode not found")
    void testFindBySkuCode_returnsEmptyWhenNotFound() {
        var result = skuRepository.findBySkuCode("NONEXISTENT");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByIdIn returns SKUs matching the given id collection")
    void testFindByIdIn_returnsMatchingSkus() {
        List<Long> ids = List.of(onShelfSku.getId(), offShelfSku.getId());

        List<ProductSku> results = skuRepository.findByIdIn(ids);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ProductSku::getSkuCode)
                .containsExactlyInAnyOrder("SKU-ON-001", "SKU-OFF-001");
    }

    @Test
    @DisplayName("save persists SKU with all fields")
    void testSave_persistsAllFields() {
        ProductSku sku = createSku("SKU-SAVE-001", "Save Test", SkuStatus.DRAFT, 5L);
        sku.setMarketPrice(new BigDecimal("199.99"));
        sku.setImage("test.jpg");
        sku.setSortOrder(10);
        sku.setSalesCount(50);
        sku.setSpecs("{\"color\":\"blue\"}");

        ProductSku saved = skuRepository.save(sku);
        entityManager.flush();
        entityManager.clear();

        ProductSku found = entityManager.find(ProductSku.class, saved.getId());
        assertThat(found).isNotNull();
        assertThat(found.getSkuCode()).isEqualTo("SKU-SAVE-001");
        assertThat(found.getName()).isEqualTo("Save Test");
        assertThat(found.getPrice()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(found.getMarketPrice()).isEqualByComparingTo(new BigDecimal("199.99"));
        assertThat(found.getStatus()).isEqualTo(SkuStatus.DRAFT);
        assertThat(found.getSpuId()).isEqualTo(5L);
        assertThat(found.getImage()).isEqualTo("test.jpg");
        assertThat(found.getSortOrder()).isEqualTo(10);
        assertThat(found.getSalesCount()).isEqualTo(50);
        assertThat(found.getSpecs()).isEqualTo("{\"color\":\"blue\"}");
    }

    private ProductSku createSku(String skuCode, String name, SkuStatus status, Long spuId) {
        ProductSku sku = new ProductSku();
        sku.setSkuCode(skuCode);
        sku.setName(name);
        sku.setPrice(new BigDecimal("99.99"));
        sku.setStatus(status);
        sku.setSpuId(spuId);
        sku.setSortOrder(0);
        sku.setSalesCount(0);
        return sku;
    }
}
