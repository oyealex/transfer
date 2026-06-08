package com.ecommerce.cart.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.query.InventoryQueryService;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.product.query.StockSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("CartValidationService")
@ExtendWith(MockitoExtension.class)
class CartValidationServiceTest {

    @Mock
    private ProductQueryService productQueryService;

    @Mock
    private InventoryQueryService inventoryQueryService;

    @InjectMocks
    private CartValidationService cartValidationService;

    private static final Long SKU_ID = 100L;

    private SkuDto skuOnShelf;

    @BeforeEach
    void setUp() {
        skuOnShelf = new SkuDto();
        skuOnShelf.setSkuId(SKU_ID);
        skuOnShelf.setName("Test SKU");
        skuOnShelf.setPrice(new BigDecimal("25.00"));
        skuOnShelf.setStatus("ON_SHELF");
    }

    @Test
    @DisplayName("validateSku passes when SKU exists and status is ON_SHELF")
    void testValidateSku_onShelf_passes() {
        when(productQueryService.getSkuForSale(SKU_ID)).thenReturn(skuOnShelf);

        SkuDto result = cartValidationService.validateSku(SKU_ID);

        assertThat(result).isNotNull();
        assertThat(result.getSkuId()).isEqualTo(SKU_ID);
        assertThat(result.getStatus()).isEqualTo("ON_SHELF");
    }

    @Test
    @DisplayName("validateSku throws BusinessException when SKU status is not ON_SHELF")
    void testValidateSku_offShelf_throwsException() {
        SkuDto offShelf = new SkuDto();
        offShelf.setSkuId(SKU_ID);
        offShelf.setStatus("OFF_SHELF");
        when(productQueryService.getSkuForSale(SKU_ID)).thenReturn(offShelf);

        // BusinessException message: "SKU <id> is not available for sale, current status: <status>"
        // BusinessException code: "SKU_NOT_AVAILABLE"
        assertThatThrownBy(() -> cartValidationService.validateSku(SKU_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not available for sale")
                .hasFieldOrPropertyWithValue("code", "SKU_NOT_AVAILABLE");
    }

    @Test
    @DisplayName("validateSku throws ResourceNotFoundException when SKU does not exist")
    void testValidateSku_notFound_throwsException() {
        when(productQueryService.getSkuForSale(SKU_ID)).thenReturn(null);

        assertThatThrownBy(() -> cartValidationService.validateSku(SKU_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("validateStock passes when available stock >= requested quantity")
    void testValidateStock_sufficient_passes() {
        StockSummaryDto stock = new StockSummaryDto(10, 0);
        when(inventoryQueryService.getStockSummary(SKU_ID)).thenReturn(stock);

        assertThatCode(() -> cartValidationService.validateStock(SKU_ID, 5))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateStock throws BusinessException when stock is insufficient")
    void testValidateStock_insufficient_throwsException() {
        StockSummaryDto stock = new StockSummaryDto(3, 0);
        when(inventoryQueryService.getStockSummary(SKU_ID)).thenReturn(stock);

        // BusinessException message: "Insufficient stock for SKU <id>: requested=<qty>, available=<stock>"
        // BusinessException code: "INSUFFICIENT_STOCK"
        assertThatThrownBy(() -> cartValidationService.validateStock(SKU_ID, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock for SKU")
                .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("validateStock throws BusinessException when stock summary is null")
    void testValidateStock_nullStock_throwsException() {
        when(inventoryQueryService.getStockSummary(SKU_ID)).thenReturn(null);

        // BusinessException message: "Insufficient stock for SKU <id>: requested=<qty>, available=0"
        // BusinessException code: "INSUFFICIENT_STOCK"
        assertThatThrownBy(() -> cartValidationService.validateStock(SKU_ID, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock for SKU")
                .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("validateQuantity passes for valid range (1 to 999)")
    void testValidateQuantity_validRange_passes() {
        assertThatCode(() -> cartValidationService.validateQuantity(1))
                .doesNotThrowAnyException();
        assertThatCode(() -> cartValidationService.validateQuantity(500))
                .doesNotThrowAnyException();
        assertThatCode(() -> cartValidationService.validateQuantity(999))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateQuantity throws BusinessException when quantity is 0")
    void testValidateQuantity_zero_throwsException() {
        // BusinessException message: "Quantity must be between 1 and 999, got: <qty>"
        // BusinessException code: "INVALID_QUANTITY"
        assertThatThrownBy(() -> cartValidationService.validateQuantity(0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Quantity must be between 1 and 999")
                .hasFieldOrPropertyWithValue("code", "INVALID_QUANTITY");
    }

    @Test
    @DisplayName("validateQuantity throws BusinessException when quantity exceeds 999")
    void testValidateQuantity_exceedsMax_throwsException() {
        // BusinessException message: "Quantity must be between 1 and 999, got: <qty>"
        // BusinessException code: "INVALID_QUANTITY"
        assertThatThrownBy(() -> cartValidationService.validateQuantity(1000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Quantity must be between 1 and 999")
                .hasFieldOrPropertyWithValue("code", "INVALID_QUANTITY");
    }

    @Test
    @DisplayName("validateQuantity throws BusinessException when quantity is negative")
    void testValidateQuantity_negative_throwsException() {
        // BusinessException message: "Quantity must be between 1 and 999, got: <qty>"
        // BusinessException code: "INVALID_QUANTITY"
        assertThatThrownBy(() -> cartValidationService.validateQuantity(-1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Quantity must be between 1 and 999")
                .hasFieldOrPropertyWithValue("code", "INVALID_QUANTITY");
    }

    @Test
    @DisplayName("validateCartSize passes when cart is below max item types")
    void testValidateCartSize_withinLimit_passes() {
        assertThatCode(() -> cartValidationService.validateCartSize(50, 1))
                .doesNotThrowAnyException();
        assertThatCode(() -> cartValidationService.validateCartSize(99, 1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateCartSize throws BusinessException when adding item exceeds max")
    void testValidateCartSize_exceedsMax_throwsException() {
        // BusinessException message: "Cart can contain at most <max> distinct items. Current: <cur>, adding: <new>"
        // BusinessException code: "CART_FULL"
        assertThatThrownBy(() -> cartValidationService.validateCartSize(100, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cart can contain at most")
                .hasFieldOrPropertyWithValue("code", "CART_FULL");
    }
}
