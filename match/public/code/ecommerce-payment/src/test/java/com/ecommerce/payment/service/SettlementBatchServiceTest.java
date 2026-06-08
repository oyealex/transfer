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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SettlementBatchService}.
 *
 * <p>The generateBatch() method includes ALL payment records
 * regardless of their payment status. It should only include payments with
 * status SUCCESS. PENDING and FAILED payments are incorrectly counted in
 * settlement totals.
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

    // ---- testGenerateBatch_includesUnpaidOrders ----

    @Test
    @DisplayName("settlement includes unpaid (PENDING/FAILED) orders")
    void testGenerateBatch_includesUnpaidOrders() {
        // Given: payments with various statuses, including non-SUCCESS
        LocalDate batchDate = LocalDate.of(2026, 6, 1);

        PaymentRecord paid1 = createPayment(1L, "PAY001", new BigDecimal("100.00"), PaymentStatus.SUCCESS);
        PaymentRecord paid2 = createPayment(2L, "PAY002", new BigDecimal("200.00"), PaymentStatus.SUCCESS);
        // These non-SUCCESS payments are included
        PaymentRecord pending = createPayment(3L, "PAY003", new BigDecimal("50.00"), PaymentStatus.PENDING);
        PaymentRecord failed = createPayment(4L, "PAY004", new BigDecimal("75.00"), PaymentStatus.FAILED);

        when(settlementBatchRepository.findByBatchDate(batchDate))
                .thenReturn(Optional.empty());
        // findByPaidAtBetween has NO status filter — returns ALL payments
        when(paymentRecordRepository.findByPaidAtBetween(any(), any()))
                .thenReturn(Arrays.asList(paid1, paid2, pending, failed));
        when(invoiceRecordRepository.findAll()).thenReturn(Collections.emptyList());

        SettlementBatch savedBatch = new SettlementBatch();
        savedBatch.setId(1L);
        savedBatch.setBatchNo("BAT20260601ABC");
        savedBatch.setBatchDate(batchDate);
        savedBatch.setTotalPaymentAmount(new BigDecimal("425.00"));
        savedBatch.setTotalRefundAmount(BigDecimal.ZERO);
        savedBatch.setTotalInvoiceAmount(BigDecimal.ZERO);
        savedBatch.setOrderCount(4);
        savedBatch.setStatus(SettlementStatus.GENERATED);

        when(settlementBatchRepository.save(any(SettlementBatch.class)))
                .thenReturn(savedBatch);
        when(settlementOrderItemRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SettlementBatchResponse response = settlementBatchService.generateBatch(batchDate);

        // Then: all 4 payments are included, including PENDING and FAILED
        assertNotNull(response);
        assertEquals(4, response.getOrderCount(),
                "orderCount=4 includes PENDING and FAILED (should be 2)");
        assertEquals(new BigDecimal("425.00"), response.getTotalPaymentAmount(),
                "total=425 includes unpaid amounts 50.00+75.00");
    }

    // ---- testGenerateBatch_calculatesTotals ----

    @Test
    @DisplayName("settlement batch calculates totals from included payments")
    void testGenerateBatch_calculatesTotals() {
        // Given: payments including non-SUCCESS ones
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

        // Then: totals include all payments
        assertEquals(3, response.getOrderCount(),
                "3 orders (only 2 are SUCCESS, 1 is PENDING)");
        assertEquals(new BigDecimal("600.00"), response.getTotalPaymentAmount(),
                "150+350+100=600 (includes pending)");

        // Verify batch was saved with correct totals
        SettlementBatch captured = batchCaptor.getValue();
        assertEquals(batchDate, captured.getBatchDate());
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
