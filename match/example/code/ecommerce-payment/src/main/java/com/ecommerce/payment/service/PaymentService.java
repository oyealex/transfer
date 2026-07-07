package com.ecommerce.payment.service;

import com.ecommerce.common.event.DomainEventPublisher;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core payment service handling payment creation, querying, and confirmation.
 *
 * <p>Payment confirmation publishes a {@link PaymentSucceededEvent} so that
 * downstream modules (logistics, loyalty, notification) handle their actions
 * asynchronously via event listeners. Post-payment actions are NOT executed
 * synchronously within the payment transaction.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentValidator paymentValidator;
    private final DomainEventPublisher eventPublisher;
    private final OrderPaymentStatusUpdater orderPaymentStatusUpdater;
    private final OrderQueryService orderQueryService;

    public PaymentService(PaymentRecordRepository paymentRecordRepository,
                          PaymentValidator paymentValidator,
                          DomainEventPublisher eventPublisher,
                          OrderPaymentStatusUpdater orderPaymentStatusUpdater,
                          OrderQueryService orderQueryService) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.paymentValidator = paymentValidator;
        this.eventPublisher = eventPublisher;
        this.orderPaymentStatusUpdater = orderPaymentStatusUpdater;
        this.orderQueryService = orderQueryService;
    }

    /**
     * Initiates a payment for an order.
     */
    @Transactional
    public PayResponse pay(PayRequest request) {
        log.info("Initiating payment for orderId={}, amount={}, method={}",
                request.getOrderId(), request.getAmount(), request.getMethod());

        // L4-01: Use OrderQueryService instead of direct DB access
        OrderDto order = orderQueryService.getPayableOrder(request.getOrderId());

        // Validate the payment request
        paymentValidator.validate(request, order);

        // Create payment record
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo(generatePaymentNo());
        payment.setOrderId(request.getOrderId());
        payment.setOrderAmount(order.getPayableAmount());
        payment.setPaidAmount(request.getAmount());
        payment.setMethod(request.getMethod());
        payment.setStatus(PaymentStatus.CREATED);
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
     * Confirms a successful payment and publishes domain event for downstream actions.
     * Logistics, loyalty points, and notifications are handled asynchronously
     * by event listeners — NOT synchronously in this transaction.
     */
    @Transactional
    public void confirmPayment(PaymentRecord payment) {
        log.info("Confirming payment: paymentNo={}, orderId={}",
                payment.getPaymentNo(), payment.getOrderId());

        // L5-01: Post-payment actions (logistics, loyalty, notification) are handled
        // asynchronously via PaymentSucceededEvent listeners. They are NOT executed
        // synchronously within the payment confirmation transaction.

        // Publish domain event for downstream modules
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                this, payment.getPaymentNo(), payment.getOrderId(),
                null, payment.getPaidAmount());
        eventPublisher.publish(event);

        log.info("Payment confirmed successfully: paymentNo={}", payment.getPaymentNo());
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
