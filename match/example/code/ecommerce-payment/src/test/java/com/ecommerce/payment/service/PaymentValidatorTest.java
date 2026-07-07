package com.ecommerce.payment.service;

import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.entity.PaymentMethod;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PaymentValidator}.
 *
 * <p>The validator only checks {@code paidAmount > 0},
 * it does NOT verify equality between request.amount and order.payableAmount.
 * This allows partial payments and over-payments to pass validation.
 */
@ExtendWith(MockitoExtension.class)
class PaymentValidatorTest {

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    private PaymentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaymentValidator(paymentRecordRepository);
    }

    // ---- testValidate_positiveAmount_passes (any positive amount passes) ----

    @Test
    @DisplayName("any positive amount passes validation (only checks >0)")
    void testValidate_positiveAmount_passes() {
        // Given: order with payableAmount 100, request with amount 50 (different)
        OrderDto order = createOrder(1L, new BigDecimal("100.00"), OrderStatus.CREATED);
        PayRequest request = new PayRequest(1L, new BigDecimal("50.00"),
                PaymentMethod.ALIPAY, "CLIENT1");

        when(paymentRecordRepository.existsByOrderIdAndStatus(eq(1L), eq(PaymentStatus.SUCCESS)))
                .thenReturn(false);

        // When/Then: 50 passes validation even though payable is 100
        assertDoesNotThrow(() -> validator.validate(request, order));
    }

    // ---- testValidate_partialPayment_passes ----

    @Test
    @DisplayName("partial payment (paidAmount < payableAmount) passes validation")
    void testValidate_partialPayment_passes() {
        // Given: payable is 200, request is only 50
        OrderDto order = createOrder(2L, new BigDecimal("200.00"), OrderStatus.CREATED);
        PayRequest request = new PayRequest(2L, new BigDecimal("50.00"),
                PaymentMethod.WECHAT, "CLIENT2");

        when(paymentRecordRepository.existsByOrderIdAndStatus(eq(2L), eq(PaymentStatus.SUCCESS)))
                .thenReturn(false);

        // When/Then: partial payment of 50 on 200 order passes
        assertDoesNotThrow(() -> validator.validate(request, order));
    }

    // ---- testValidate_overPayment_passes ----

    @Test
    @DisplayName("over-payment (paidAmount > payableAmount) passes validation")
    void testValidate_overPayment_passes() {
        // Given: payable is 100, request is 999
        OrderDto order = createOrder(3L, new BigDecimal("100.00"), OrderStatus.PAYING);
        PayRequest request = new PayRequest(3L, new BigDecimal("999.00"),
                PaymentMethod.BALANCE, "CLIENT3");

        when(paymentRecordRepository.existsByOrderIdAndStatus(eq(3L), eq(PaymentStatus.SUCCESS)))
                .thenReturn(false);

        // When/Then: over-payment of 999 on 100 order passes
        assertDoesNotThrow(() -> validator.validate(request, order));
    }

    // ---- testValidate_zeroAmount_fails ----

    @Test
    @DisplayName("zero amount fails validation (only check that does work)")
    void testValidate_zeroAmount_fails() {
        // Given: paidAmount is 0
        OrderDto order = createOrder(4L, new BigDecimal("100.00"), OrderStatus.CREATED);
        PayRequest request = new PayRequest(4L, BigDecimal.ZERO,
                PaymentMethod.ALIPAY, "CLIENT4");

        // When/Then: zero amount is correctly rejected
        assertThrows(ValidationException.class,
                () -> validator.validate(request, order));
    }

    // ---- testValidate_negativeAmount_fails ----

    @Test
    @DisplayName("negative amount fails validation")
    void testValidate_negativeAmount_fails() {
        // Given: paidAmount is -10
        OrderDto order = createOrder(5L, new BigDecimal("100.00"), OrderStatus.CREATED);
        PayRequest request = new PayRequest(5L, new BigDecimal("-10.00"),
                PaymentMethod.ALIPAY, "CLIENT5");

        // When/Then: negative amount is correctly rejected
        assertThrows(ValidationException.class,
                () -> validator.validate(request, order));
    }

    // ---- testValidate_exactMatch_passes ----

    @Test
    @DisplayName("exact amount match passes validation (coincidentally correct)")
    void testValidate_exactMatch_passes() {
        // Given: payable is 99.99, request is exactly 99.99
        OrderDto order = createOrder(6L, new BigDecimal("99.99"), OrderStatus.CREATED);
        PayRequest request = new PayRequest(6L, new BigDecimal("99.99"),
                PaymentMethod.ALIPAY, "CLIENT6");

        when(paymentRecordRepository.existsByOrderIdAndStatus(eq(6L), eq(PaymentStatus.SUCCESS)))
                .thenReturn(false);

        // When/Then: exact match passes because >0 check is satisfied
        assertDoesNotThrow(() -> validator.validate(request, order));
    }

    // ---- helper ----

    private OrderDto createOrder(Long orderId, BigDecimal payableAmount, OrderStatus status) {
        OrderDto dto = new OrderDto();
        dto.setOrderId(orderId);
        dto.setOrderNo("ORD" + orderId);
        dto.setUserId(100L);
        dto.setPayableAmount(payableAmount);
        dto.setStatus(status);
        return dto;
    }
}
