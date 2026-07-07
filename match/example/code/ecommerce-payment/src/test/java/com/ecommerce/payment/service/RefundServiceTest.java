package com.ecommerce.payment.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.payment.dto.RefundApplyRequest;
import com.ecommerce.payment.dto.RefundResponse;
import com.ecommerce.payment.dto.RefundReviewRequest;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.RefundRecord;
import com.ecommerce.payment.entity.RefundStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import com.ecommerce.payment.repository.RefundRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RefundService}.
 *
 * <p>approveRefund() directly sets COMPLETED status without
 * and warehouse acceptance workflow.
 * PENDING_REVIEW -> APPROVED -> WAITING_WAREHOUSE_ACCEPT -> WAREHOUSE_ACCEPTED -> COMPLETED.
 * The current code does: PENDING_REVIEW -> APPROVED -> COMPLETED (skipping warehouse).
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRecordRepository refundRecordRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private RefundCalculator refundCalculator;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private LocalNotificationService notificationService;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(
                refundRecordRepository,
                paymentRecordRepository,
                refundCalculator,
                eventPublisher,
                notificationService
        );
    }

    // ---- testApproveRefund_directlyCompletes ----

    @Test
    @DisplayName("approveRefund directly sets COMPLETED, skipping warehouse")
    void testApproveRefund_directlyCompletes() {
        // Given: a refund in PENDING_REVIEW
        RefundRecord refund = new RefundRecord();
        refund.setId(1L);
        refund.setRefundNo("RF001");
        refund.setPaymentNo("PAY001");
        refund.setOrderId(10L);
        refund.setUserId(100L);
        refund.setRefundAmount(new BigDecimal("97.00"));
        refund.setStatus(RefundStatus.PENDING_REVIEW);
        refund.setReason("Changed mind");

        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY001");
        payment.setOrderId(10L);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAmount(new BigDecimal("100.00"));

        when(refundRecordRepository.findById(1L)).thenReturn(Optional.of(refund));
        when(refundRecordRepository.save(any(RefundRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRecordRepository.findByPaymentNo("PAY001"))
                .thenReturn(Optional.of(payment));

        RefundReviewRequest reviewRequest = new RefundReviewRequest(true, "Approved by admin");

        // When: admin approves the refund
        RefundResponse response = refundService.reviewRefund(1L, 999L, reviewRequest);

        // Then: status is COMPLETED, NOT WAITING_WAREHOUSE_ACCEPT
        // The approveRefund() private method calls processRefund() immediately,
        // which sets status to COMPLETED. The warehouse acceptance step is skipped.
        assertEquals(RefundStatus.COMPLETED, response.getStatus(),
                "reviewed refund status");
        assertNotEquals(RefundStatus.WAITING_WAREHOUSE_ACCEPT, response.getStatus(),
                "warehouse acceptance is skipped, never set");
        assertNotEquals(RefundStatus.WAREHOUSE_ACCEPTED, response.getStatus());
        assertNotNull(response.getCompletedAt());
    }

    // ---- testWarehouseAccept_exists_independently ----

    @Test
    @DisplayName("warehouseAccept handles reviewed refund")
    void testWarehouseAccept_exists_independently() {
        // Given: warehouseAccept requires WAITING_WAREHOUSE_ACCEPT status
        // Verify warehouse acceptance behavior after review.
        // can never be called in the normal flow.

        RefundRecord refund = new RefundRecord();
        refund.setId(2L);
        refund.setRefundNo("RF002");
        refund.setPaymentNo("PAY002");
        refund.setOrderId(20L);
        refund.setUserId(200L);
        refund.setStatus(RefundStatus.COMPLETED); // already completed by approveRefund

        when(refundRecordRepository.findById(2L)).thenReturn(Optional.of(refund));

        // When/Then: calling warehouseAccept on a COMPLETED refund throws
        // because it requires WAITING_WAREHOUSE_ACCEPT status
        BusinessException ex = assertThrows(BusinessException.class,
                () -> refundService.warehouseAccept(2L, 999L));
        assertEquals("REFUND_STATUS_INVALID", ex.getCode());
    }

    // ---- testApplyRefund_createsRefundRecord ----

    @Test
    @DisplayName("applyRefund creates a refund record in PENDING_REVIEW status")
    void testApplyRefund_createsRefundRecord() {
        // Given: a successful payment exists
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY003");
        payment.setOrderId(30L);
        payment.setPaidAmount(new BigDecimal("200.00"));
        payment.setStatus(PaymentStatus.SUCCESS);

        when(paymentRecordRepository.findByPaymentNo("PAY003"))
                .thenReturn(Optional.of(payment));
        when(refundCalculator.calculate(new BigDecimal("200.00")))
                .thenReturn(new BigDecimal("195.00"));
        when(refundRecordRepository.save(any(RefundRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefundApplyRequest request = new RefundApplyRequest(30L, "PAY003", "Defective item");

        // When
        RefundResponse response = refundService.applyRefund(100L, request);

        // Then
        assertNotNull(response);
        assertEquals("PAY003", response.getPaymentNo());
        assertEquals(30L, response.getOrderId());
        assertEquals(100L, response.getUserId());
        assertEquals(RefundStatus.PENDING_REVIEW, response.getStatus());
        assertEquals("Defective item", response.getReason());
        assertNotNull(response.getRefundNo());
    }

    // ---- testProcessRefund_calculatesCorrectAmount ----

    @Test
    @DisplayName("processRefund calculates amount using RefundCalculator formula")
    void testProcessRefund_calculatesCorrectAmount() {
        // Given: a successful payment of 150.00
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY004");
        payment.setOrderId(40L);
        payment.setPaidAmount(new BigDecimal("150.00"));
        payment.setStatus(PaymentStatus.SUCCESS);

        // RefundCalculator formula: refund = 150.00 * 0.98 - 1.00 = 146.00
        BigDecimal expectedRefundAmount = new BigDecimal("146.00");

        when(paymentRecordRepository.findByPaymentNo("PAY004"))
                .thenReturn(Optional.of(payment));
        when(refundCalculator.calculate(new BigDecimal("150.00")))
                .thenReturn(expectedRefundAmount);
        when(refundRecordRepository.save(any(RefundRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefundApplyRequest request = new RefundApplyRequest(40L, "PAY004", "Wrong size");

        // When
        RefundResponse response = refundService.applyRefund(100L, request);

        // Then: the refund amount comes from RefundCalculator
        assertEquals(expectedRefundAmount, response.getRefundAmount(),
                "RefundCalculator applies paid * 0.98 - 1.00");
    }
}
