package com.ecommerce.payment.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.order.query.OrderPaymentStatusUpdater;
import com.ecommerce.order.query.OrderQueryService;
import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.dto.PayResponse;
import com.ecommerce.payment.entity.PaymentMethod;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PaymentService}, covering JdbcTemplate usage
 * and synchronous post-actions in transaction.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private PaymentValidator paymentValidator;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private LocalNotificationService notificationService;

    @Mock
    private OrderPaymentStatusUpdater orderPaymentStatusUpdater;

    @Mock
    private OrderQueryService orderQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRecordRepository,
                paymentValidator,
                eventPublisher,
                notificationService,
                orderPaymentStatusUpdater,
                orderQueryService,
                jdbcTemplate
        );
    }

    // ---- testPay_validRequest_createsPaymentRecord ----

    @Test
    @DisplayName("pay() should create a PaymentRecord for a valid request")
    void testPay_validRequest_createsPaymentRecord() {
        // Given
        PayRequest request = new PayRequest(1L, new BigDecimal("99.00"),
                PaymentMethod.ALIPAY, "CLIENT123");

        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setOrderNo("ORD001");
        orderDto.setUserId(100L);
        orderDto.setPayableAmount(new BigDecimal("99.00"));
        orderDto.setStatus(com.ecommerce.order.entity.OrderStatus.CREATED);

        // JdbcTemplate is used directly to query order
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(orderDto);

        PaymentRecord savedRecord = new PaymentRecord();
        savedRecord.setPaymentNo("PAY123");
        savedRecord.setOrderId(1L);
        savedRecord.setPaidAmount(new BigDecimal("99.00"));
        savedRecord.setStatus(PaymentStatus.PENDING);
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PayResponse response = paymentService.pay(request);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.getOrderId());
        assertEquals(PaymentStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("99.00"), response.getPaidAmount());
        assertNotNull(response.getPaymentNo());

        // Verify JdbcTemplate was used directly to query order
        verify(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), eq(1L));
        // Verify OrderQueryService was NOT used (bypasses interface)
        verify(orderQueryService, never()).getPayableOrder(any());
        verify(orderQueryService, never()).getOrder(any());

        // Verify payment was saved
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
        verify(paymentValidator).validate(eq(request), eq(orderDto));
    }

    // ---- testConfirmPayment_synchronousPostActions ----

    @Test
    @DisplayName("confirmPayment() executes logistics/loyalty/notification synchronously in same transaction")
    void testConfirmPayment_synchronousPostActions() {
        // Given: a payment record to confirm
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY001");
        payment.setOrderId(1L);
        payment.setPaidAmount(new BigDecimal("99.00"));
        payment.setStatus(PaymentStatus.PENDING);

        // When: confirmPayment is called
        paymentService.confirmPayment(payment);

        // Then: notificationService.send() is called SYNCHRONOUSLY
        // inside the confirmPayment transaction.
        // Verify the notification service was called with correct parameters
        ArgumentCaptor<NotificationRequest> notificationCaptor =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(notificationCaptor.capture());

        NotificationRequest sent = notificationCaptor.getValue();
        assertEquals("PAYMENT_SUCCESS", sent.getBizType());
        assertEquals("PAY001", sent.getBizId());
        assertEquals("payment_success", sent.getTemplateCode());
        assertEquals("pay_notify_PAY001", sent.getIdempotencyKey());

        // Verify event was published synchronously too
        verify(eventPublisher).publish(any());
    }

    // ---- testConfirmPayment_postActionFailure_rollsBackPayment ----

    @Test
    @DisplayName("confirmPayment() should fail entirely if any post-action throws (rollback behavior)")
    void testConfirmPayment_postActionFailure_rollsBackPayment() {
        // Given: notificationService will throw, simulating a downstream failure
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo("PAY001");
        payment.setOrderId(1L);
        payment.setPaidAmount(new BigDecimal("99.00"));
        payment.setStatus(PaymentStatus.PENDING);

        doThrow(new RuntimeException("Notification service down"))
                .when(notificationService).send(any(NotificationRequest.class));

        // When / Then: the entire confirmPayment fails because
        // notification failure propagates within the same transaction.
        // The RuntimeException should propagate out of confirmPayment.
        assertThrows(RuntimeException.class, () -> paymentService.confirmPayment(payment));

        // Consequence: the notification failure causes the entire
        // payment confirmation to fail, even though payment was successful.
        // In the real application with @Transactional, this would roll back
        // the payment status change too.
    }

    // ---- testConfirmPayment_usesJdbcTemplate ----

    @Test
    @DisplayName("pay() queries order data for payment")
    void testConfirmPayment_usesJdbcTemplate() {
        // Given
        PayRequest request = new PayRequest(1L, new BigDecimal("50.00"),
                PaymentMethod.WECHAT, "CLIENT456");

        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(1L);
        orderDto.setPayableAmount(new BigDecimal("50.00"));
        orderDto.setStatus(com.ecommerce.order.entity.OrderStatus.CREATED);

        // JdbcTemplate is used directly
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(orderDto);
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        paymentService.pay(request);

        // Then: JdbcTemplate is called directly
        // Verify order lookup dependencies.
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), any(RowMapper.class), eq(1L));
        verify(orderQueryService, never()).getPayableOrder(any());
        verify(orderQueryService, never()).getOrder(any());
    }

    // ---- testGetPayment_returnsPaymentRecord ----

    @Test
    @DisplayName("getPayment() should return PaymentRecord by paymentNo")
    void testGetPayment_returnsPaymentRecord() {
        // Given
        String paymentNo = "PAY123";
        PaymentRecord record = new PaymentRecord();
        record.setPaymentNo(paymentNo);
        record.setOrderId(1L);
        record.setPaidAmount(new BigDecimal("99.00"));
        record.setStatus(PaymentStatus.SUCCESS);
        record.setCreatedAt(java.time.LocalDateTime.now());

        when(paymentRecordRepository.findByPaymentNo(paymentNo))
                .thenReturn(Optional.of(record));

        // When
        PayResponse response = paymentService.getPayment(paymentNo);

        // Then
        assertNotNull(response);
        assertEquals(paymentNo, response.getPaymentNo());
        assertEquals(1L, response.getOrderId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        assertEquals(new BigDecimal("99.00"), response.getPaidAmount());
    }
}
