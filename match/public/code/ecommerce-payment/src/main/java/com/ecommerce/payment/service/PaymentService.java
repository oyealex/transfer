package com.ecommerce.payment.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.notification.LocalNotificationService;
import com.ecommerce.common.notification.NotificationChannel;
import com.ecommerce.common.notification.NotificationRequest;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.order.query.OrderPaymentStatusUpdater;
import com.ecommerce.order.query.OrderQueryService;
import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.dto.PayResponse;
import com.ecommerce.payment.entity.PaymentRecord;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.event.PaymentSucceededEvent;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Core payment service handling payment creation, querying, and confirmation.
 *
 * <p>The {@link #confirmPayment(PaymentRecord)} method
 * is annotated with {@code @Transactional} and synchronously executes
 * logistics creation, loyalty point earning, and notification sending
 * all within the same database transaction. If any of these non-critical
 * post-payment actions fails, the entire transaction rolls back, causing
 * the payment confirmation itself to fail.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentValidator paymentValidator;
    private final DomainEventPublisher eventPublisher;
    private final LocalNotificationService notificationService;
    private final OrderPaymentStatusUpdater orderPaymentStatusUpdater;

    private final OrderQueryService orderQueryService;

    private final JdbcTemplate jdbcTemplate;

    public PaymentService(PaymentRecordRepository paymentRecordRepository,
                          PaymentValidator paymentValidator,
                          DomainEventPublisher eventPublisher,
                          LocalNotificationService notificationService,
                          OrderPaymentStatusUpdater orderPaymentStatusUpdater,
                          OrderQueryService orderQueryService,
                          JdbcTemplate jdbcTemplate) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.paymentValidator = paymentValidator;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.orderPaymentStatusUpdater = orderPaymentStatusUpdater;
        this.orderQueryService = orderQueryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Initiates a payment for an order.
     */
    @Transactional
    public PayResponse pay(PayRequest request) {
        log.info("Initiating payment for orderId={}, amount={}, method={}",
                request.getOrderId(), request.getAmount(), request.getMethod());

        OrderDto order = queryOrderDirectly(request.getOrderId());

        // Validate the payment request
        paymentValidator.validate(request, order);

        // Create payment record
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo(generatePaymentNo());
        payment.setOrderId(request.getOrderId());
        payment.setOrderAmount(order.getPayableAmount());
        payment.setPaidAmount(request.getAmount());
        payment.setMethod(request.getMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setClientPaymentNo(request.getClientPaymentNo());

        payment = paymentRecordRepository.save(payment);

        log.info("Payment record created: paymentNo={}, orderId={}",
                payment.getPaymentNo(), payment.getOrderId());

        return toPayResponse(payment);
    }

    /**
     * Retrieves a payment record by payment number.
     */
    public PayResponse getPayment(String paymentNo) {
        PaymentRecord payment = paymentRecordRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentRecord", paymentNo));
        return toPayResponse(payment);
    }

    /**
     * Confirms a successful payment and triggers downstream actions.
     *
     * <p>This method is annotated with {@code @Transactional}
     * and synchronously calls createLogistics(), earnPoints(), and
     * sendNotifications() within the same transaction boundary. If any of
     * these non-critical post-payment actions fails (e.g., notification
     * service is down), the entire transaction rolls back, causing the
     * payment to be unconfirmed even though payment was actually successful.
     */
    @Transactional
    public void confirmPayment(PaymentRecord payment) {
        log.info("Confirming payment: paymentNo={}, orderId={}",
                payment.getPaymentNo(), payment.getOrderId());

        // All post-payment actions executed synchronously
        // in the same transaction, causing coupling and failure propagation.

        // 1. Create logistics shipment (synchronous — belongs in separate module)
        createLogistics(payment);

        // 2. Earn loyalty points (synchronous — belongs in separate module)
        earnPoints(payment);

        // 3. Send notifications (synchronous — belongs in separate module)
        sendNotifications(payment);

        // 4. Publish domain event
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                this, payment.getPaymentNo(), payment.getOrderId(),
                null, payment.getPaidAmount());
        eventPublisher.publish(event);

        log.info("Payment confirmed successfully: paymentNo={}", payment.getPaymentNo());
    }

    /**
     * Queries an order directly from the database using JdbcTemplate.
     */
    private OrderDto queryOrderDirectly(Long orderId) {
        // Fault injection check
        if (com.ecommerce.common.test.FaultInjectionRegistry.isActive("order-query-service-unavailable")) {
            throw new RuntimeException("Fault injected: order-query-service-unavailable");
        }

        String sql = "SELECT id AS order_id, order_no, user_id, status, payable_amount, " +
                "payment_no, created_at FROM orders WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                OrderDto dto = new OrderDto();
                dto.setOrderId(rs.getLong("order_id"));
                dto.setOrderNo(rs.getString("order_no"));
                dto.setUserId(rs.getLong("user_id"));
                String statusStr = rs.getString("status");
                if (statusStr != null) {
                    dto.setStatus(com.ecommerce.order.entity.OrderStatus.valueOf(statusStr));
                }
                BigDecimal payable = rs.getBigDecimal("payable_amount");
                dto.setPayableAmount(payable != null ? payable : BigDecimal.ZERO);
                dto.setPaymentNo(rs.getString("payment_no"));
                java.sql.Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) {
                    dto.setCreatedAt(ts.toLocalDateTime());
                }
                return dto;
            }, orderId);
        } catch (Exception e) {
            throw new BusinessException("ORDER_NOT_FOUND",
                    "Order not found: " + orderId, e);
        }
    }

    // ---- Synchronous post-payment actions ----

    private void createLogistics(PaymentRecord payment) {
        // Synchronous cross-module call in payment transaction
        log.info("Creating logistics for payment: paymentNo={}", payment.getPaymentNo());
        // In a real implementation this would call the logistics module
        // Any failure here would roll back the payment confirmation
    }

    private void earnPoints(PaymentRecord payment) {
        // Synchronous cross-module call in payment transaction
        log.info("Earning loyalty points for payment: paymentNo={}", payment.getPaymentNo());
        // In a real implementation this would call the loyalty module
        // Any failure here would roll back the payment confirmation
    }

    private void sendNotifications(PaymentRecord payment) {
        // Synchronous notification send in payment transaction
        log.info("Sending notifications for payment: paymentNo={}", payment.getPaymentNo());
        NotificationRequest request = new NotificationRequest();
        request.setBizType("PAYMENT_SUCCESS");
        request.setBizId(payment.getPaymentNo());
        request.setChannel(NotificationChannel.EMAIL);
        request.setTemplateCode("payment_success");
        request.setVariables(Map.of(
                "paymentNo", payment.getPaymentNo(),
                "amount", payment.getPaidAmount().toString()
        ));
        request.setIdempotencyKey("pay_notify_" + payment.getPaymentNo());
        notificationService.send(request);
    }

    // ---- Utility ----

    private String generatePaymentNo() {
        return "PAY" + System.currentTimeMillis() + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private PayResponse toPayResponse(PaymentRecord payment) {
        PayResponse response = new PayResponse();
        response.setPaymentNo(payment.getPaymentNo());
        response.setOrderId(payment.getOrderId());
        response.setStatus(payment.getStatus());
        response.setPaidAmount(payment.getPaidAmount());
        response.setCreatedAt(payment.getCreatedAt());
        return response;
    }
}
