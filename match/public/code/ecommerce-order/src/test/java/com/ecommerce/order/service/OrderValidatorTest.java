package com.ecommerce.order.service;

import com.ecommerce.common.exception.OrderValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OrderValidator}.
 */
@DisplayName("OrderValidator")
class OrderValidatorTest {

    private final OrderValidator validator = new OrderValidator();

    // ======================== validateAmount ========================

    @Test
    @DisplayName("validateAmount with zero amount throws OrderValidationException")
    void testValidateAmount_zero_throwsOrderValidationException() {
        assertThatThrownBy(() -> validator.validateAmount(BigDecimal.ZERO))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Order amount must be positive");
    }

    @Test
    @DisplayName("validateAmount with negative amount throws OrderValidationException")
    void testValidateAmount_negative_throwsOrderValidationException() {
        assertThatThrownBy(() -> validator.validateAmount(new BigDecimal("-50.00")))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Order amount must be positive");
    }

    @Test
    @DisplayName("validateAmount with null amount throws OrderValidationException")
    void testValidateAmount_null_throwsOrderValidationException() {
        assertThatThrownBy(() -> validator.validateAmount(null))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Order amount must be positive");
    }

    @Test
    @DisplayName("validateAmount with positive amount passes without exception")
    void testValidateAmount_positive_noException() {
        assertThatCode(() -> validator.validateAmount(new BigDecimal("100.00")))
                .doesNotThrowAnyException();

        assertThatCode(() -> validator.validateAmount(new BigDecimal("0.01")))
                .doesNotThrowAnyException();
    }

    // ======================== validateQuantity ========================

    @Test
    @DisplayName("validateQuantity with zero throws BusinessException")
    void testValidateQuantity_zero_throwsBusinessException() {
        assertThatThrownBy(() -> validator.validateQuantity(0))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    @DisplayName("validateQuantity with negative throws BusinessException")
    void testValidateQuantity_negative_throwsBusinessException() {
        assertThatThrownBy(() -> validator.validateQuantity(-1))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    @DisplayName("validateQuantity with positive value passes")
    void testValidateQuantity_positive_noException() {
        assertThatCode(() -> validator.validateQuantity(1)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateQuantity(10)).doesNotThrowAnyException();
    }

    // ======================== validateItemsCount ========================

    @Test
    @DisplayName("validateItemsCount with zero throws BusinessException")
    void testValidateItemsCount_zero_throwsBusinessException() {
        assertThatThrownBy(() -> validator.validateItemsCount(0))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    @DisplayName("validateItemsCount with negative throws BusinessException")
    void testValidateItemsCount_negative_throwsBusinessException() {
        assertThatThrownBy(() -> validator.validateItemsCount(-5))
                .isInstanceOf(com.ecommerce.common.exception.BusinessException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    @DisplayName("validateItemsCount with positive value passes")
    void testValidateItemsCount_positive_noException() {
        assertThatCode(() -> validator.validateItemsCount(1)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateItemsCount(5)).doesNotThrowAnyException();
    }
}
