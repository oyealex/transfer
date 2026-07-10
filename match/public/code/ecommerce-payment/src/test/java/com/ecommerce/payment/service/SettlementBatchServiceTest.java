package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.SettlementBatchResponse;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.entity.SettlementBatch;
import com.ecommerce.payment.entity.SettlementStatus;
import com.ecommerce.payment.repository.InvoiceRecordRepository;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import com.ecommerce.payment.repository.SettlementBatchRepository;
import com.ecommerce.payment.repository.SettlementOrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SettlementBatchService}.
 *
 * <p>The generateBatch() method only includes payment records with
 * status SUCCESS in settlement totals and settlement order items.
 */
@ExtendWith(MockitoExtension.class)
class SettlementBatchServiceTest {

    @Mock
    private SettlementBatchRepository settlementBatchRepository;

    @Mock
    private SettlementOrderItemRepository settlementOrderItemRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private InvoiceRecordRepository invoiceRecordRepository;

    private SettlementBatchService settlementBatchService;

    @BeforeEach
    void setUp() {
        settlementBatchService = new SettlementBatchService(
                settlementBatchRepository,
                settlementOrderItemRepository,
                paymentRecordRepository,
                invoiceRecordRepository
        );
    }

    // ---- testGenerateBatch_excludesUnpaidOrders ----

    @Test
    @DisplayName("settlement excludes unpaid (PENDING/FAILED) orders")
    void testGenerateBatch_excludesUnpaidOrders() {
        // Given: payments with various statuses, including non-SUCCESS
        LocalDate batchDate = LocalDate.of(2026, 6, 1);

        PaymentRecord paid1 = createPayment(1L, "PAY001", new BigDecimal("100.00"), PaymentStatus.SUCCESS);
        PaymentRecord paid2 = createPayment(2L, "PAY002", new BigDecimal("200.00"), PaymentStatus.SUCCESS);
        PaymentRecord pending = createPayment(3L, "PAY003", new BigDecimal("50.00"), PaymentStatus.PENDING);
        PaymentRecord failed = createPayment(4L, "PAY004", new BigDecimal("75.00"), PaymentStatus.FAILED);

        when(settlementBatchRepository.findByBatchDate(batchDate))
                .thenReturn(Optional.empty());
        // Repository returns all records in the time window; service filters by status.
        when(paymentRecordRepository.findByPaidAtBetween(any(), any()))
                .thenReturn(Arrays.asList(paid1, paid2, pending, failed));
        when(invoiceRecordRepository.findAll()).thenReturn(Collections.emptyList());

        when(settlementBatchRepository.save(any(SettlementBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(settlementOrderItemRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SettlementBatchResponse response = settlementBatchService.generateBatch(batchDate);

        // Then: only the 2 successful payments are included.
        assertNotNull(response);
        assertEquals(2, response.getOrderCount(),
                "settlement order count");
        assertEquals(new BigDecimal("300.00"), response.getTotalPaymentAmount(),
                "total=300 excludes pending and failed amounts");
        verify(settlementOrderItemRepository, times(2)).save(any());
    }

    // ---- testGenerateBatch_calculatesTotals ----

    @Test
    @DisplayName("settlement batch calculates totals from included payments")
    void testGenerateBatch_calculatesTotals() {
        // Given: payments including a non-SUCCESS record
        LocalDate batchDate = LocalDate.of(2026, 6, 2);

        PaymentRecord payment1 = createPayment(10L, "PAY010", new BigDecimal("150.00"), PaymentStatus.SUCCESS);
        PaymentRecord payment2 = createPayment(20L, "PAY020", new BigDecimal("350.00"), PaymentStatus.SUCCESS);
        PaymentRecord pending = createPayment(30L, "PAY030", new BigDecimal("100.00"), PaymentStatus.PENDING);

        when(settlementBatchRepository.findByBatchDate(batchDate))
                .thenReturn(Optional.empty());
        when(paymentRecordRepository.findByPaidAtBetween(any(), any()))
                .thenReturn(Arrays.asList(payment1, payment2, pending));
        when(invoiceRecordRepository.findAll()).thenReturn(Collections.emptyList());

        ArgumentCaptor<SettlementBatch> batchCaptor =
                ArgumentCaptor.forClass(SettlementBatch.class);
        when(settlementBatchRepository.save(batchCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(settlementOrderItemRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SettlementBatchResponse response = settlementBatchService.generateBatch(batchDate);

        // Then: totals include only successful payments
        assertEquals(2, response.getOrderCount(),
                "2 successful orders; pending order is excluded");
        assertEquals(new BigDecimal("500.00"), response.getTotalPaymentAmount(),
                "150+350=500");

        // Verify batch was saved with correct totals
        SettlementBatch captured = batchCaptor.getValue();
        assertEquals(batchDate, captured.getBatchDate());
        assertEquals(new BigDecimal("500.00"), captured.getTotalPaymentAmount());
        assertEquals(2, captured.getOrderCount());
        assertEquals(SettlementStatus.GENERATED, captured.getStatus());
        assertNotNull(captured.getBatchNo());
    }

    // ---- helper ----

    private PaymentRecord createPayment(Long orderId, String paymentNo,
                                         BigDecimal paidAmount, PaymentStatus status) {
        PaymentRecord p = new PaymentRecord();
        p.setPaymentNo(paymentNo);
        p.setOrderId(orderId);
        p.setPaidAmount(paidAmount);
        p.setStatus(status);
        return p;
    }
}
