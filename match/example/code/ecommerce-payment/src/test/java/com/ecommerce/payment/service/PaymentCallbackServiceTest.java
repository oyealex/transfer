package com.ecommerce.payment.service;

import com.ecommerce.order.query.OrderPaymentStatusUpdater;
import com.ecommerce.payment.dto.PaymentCallbackRequest;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PaymentCallbackService}.
 *
 * <p>The processCallback() method does NOT verify the
 * X-Payment-Signature header. It logs the signature but never validates it,
 * making the callback endpoint vulnerable to forged payment notifications.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCallbackServiceTest {

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private OrderPaymentStatusUpdater orderPaymentStatusUpdater;

    @Mock
    private PaymentService paymentService;

    private PaymentCallbackService callbackService;

    @BeforeEach
    void setUp() {
        callbackService = new PaymentCallbackService(
                paymentRecordRepository,
                orderPaymentStatusUpdater,
                paymentService
        );
    }

    // ---- testProcessCallback_withoutSignatureVerification_succeeds ----

    @Test
    @DisplayName("callback is processed WITHOUT signature verification")
    void testProcessCallback_withoutSignatureVerification_succeeds() {
        // Given: a callback with an obviously forged/wrong signature
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY001", 1L, "SUCCESS",
                new BigDecimal("99.00"), "seq-001",
                "WRONG_SIGNATURE_FORGED" // This signature is never validated
        );

        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY001");
        payment.setOrderId(1L);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaidAmount(new BigDecimal("99.00"));

        when(paymentRecordRepository.findByPaymentNo("PAY001"))
                .thenReturn(Optional.of(payment));
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: processCallback is called with the forged signature
        callbackService.processCallback(request);

        // Then: callback is processed successfully WITHOUT verifying
        // the signature. The wrong signature "WRONG_SIGNATURE_FORGED" is simply
        // logged but never validated. No exception is thrown.
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
        verify(orderPaymentStatusUpdater).markAsPaid(1L, "PAY001");
        verify(paymentService).confirmPayment(any(PaymentRecord.class));
    }

    // ---- testProcessCallback_updatesPaymentStatus ----

    @Test
    @DisplayName("successful callback updates payment status to SUCCESS")
    void testProcessCallback_updatesPaymentStatus() {
        // Given
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY002", 2L, "SUCCESS",
                new BigDecimal("199.00"), "seq-002",
                "some_signature" // Signature is not validated
        );

        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY002");
        payment.setOrderId(2L);
        payment.setPaidAmount(new BigDecimal("199.00"));
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentRecordRepository.findByPaymentNo("PAY002"))
                .thenReturn(Optional.of(payment));
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        callbackService.processCallback(request);

        // Then: payment status should be updated to SUCCESS
        ArgumentCaptor<PaymentRecord> captor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRecordRepository).save(captor.capture());
        PaymentRecord saved = captor.getValue();
        assertEquals(PaymentStatus.SUCCESS, saved.getStatus());
        assertEquals(new BigDecimal("199.00"), saved.getPaidAmount());
        assertEquals("seq-002", saved.getCallbackSequence());
        assertNotNull(saved.getPaidAt());
    }

    // ---- testProcessCallback_duplicateCallback_handledIdempotently ----

    @Test
    @DisplayName("duplicate callback with same sequence is handled idempotently")
    void testProcessCallback_duplicateCallback_handledIdempotently() {
        // Given: payment already has the same callback sequence
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "PAY003", 3L, "SUCCESS",
                new BigDecimal("299.00"), "seq-003",
                "sig-duplicate"
        );

        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY003");
        payment.setOrderId(3L);
        payment.setPaidAmount(new BigDecimal("299.00"));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCallbackSequence("seq-003"); // already processed with this sequence

        when(paymentRecordRepository.findByPaymentNo("PAY003"))
                .thenReturn(Optional.of(payment));

        // When: same callback sequence arrives again
        callbackService.processCallback(request);

        // Then: idempotent — no save, no confirm, no status update
        verify(paymentRecordRepository, never()).save(any());
        verify(paymentService, never()).confirmPayment(any());
        verify(orderPaymentStatusUpdater, never()).markAsPaid(any(), any());
    }
}
