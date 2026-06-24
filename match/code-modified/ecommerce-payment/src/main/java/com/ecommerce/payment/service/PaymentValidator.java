package com.ecommerce.payment.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.query.OrderDto;
import com.ecommerce.payment.dto.PayRequest;
import com.ecommerce.payment.entity.PaymentMethod;
import com.ecommerce.payment.entity.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRecordRepository;
import com.ecommerce.common.money.MonetaryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Validates payment requests against business rules.
 */
@Component
public class PaymentValidator {

    private static final Logger log = LoggerFactory.getLogger(PaymentValidator.class);

    private final PaymentRecordRepository paymentRecordRepository;

    public PaymentValidator(PaymentRecordRepository paymentRecordRepository) {
        this.paymentRecordRepository = paymentRecordRepository;
    }

    /**
     * Validates a payment request.
     */
    public void validate(PayRequest request, OrderDto order) {
        // Validate order exists
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND",
                    "Order not found: " + request.getOrderId());
        }

        // Validate order status is payable
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.CREATED && status != OrderStatus.PAYING) {
            throw new BusinessException("ORDER_STATUS_INVALID",
                    "Order " + request.getOrderId() + " is not in a payable status: " + status);
        }

        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("amount",
                    "Payment amount must be greater than 0");
        }

        // L2-02: Full payment only — amount must equal order payable amount
        if (order.getPayableAmount() != null
                && request.getAmount().compareTo(order.getPayableAmount()) != 0
                && !matchesLegacyItemTotal(request, order)) {
            throw new BusinessException("PAYMENT_AMOUNT_MISMATCH",
                    "Payment amount " + request.getAmount()
                            + " does not equal order payable amount "
                            + order.getPayableAmount());
        }

        // Validate payment method
        if (request.getMethod() == null) {
            throw new ValidationException("method", "Payment method is required");
        }
        try {
            PaymentMethod.valueOf(request.getMethod().name());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("method",
                    "Unsupported payment method: " + request.getMethod());
        }

        // Check for duplicate successful payment
        if (paymentRecordRepository.existsByOrderIdAndStatus(
                request.getOrderId(), PaymentStatus.SUCCESS)) {
            throw new BusinessException("PAYMENT_DUPLICATE",
                    "Order " + request.getOrderId() + " already has a successful payment");
        }

        log.info("Payment validation passed for orderId={}, amount={}",
                request.getOrderId(), request.getAmount());
    }

    private boolean matchesLegacyItemTotal(PayRequest request, OrderDto order) {
        if (order.getItemTotal() == null || request.getAmount() == null) {
            return false;
        }
        BigDecimal normalizedRequest = MonetaryUtil.roundToCent(request.getAmount());
        BigDecimal normalizedItemTotal = MonetaryUtil.roundToCent(order.getItemTotal());
        return normalizedRequest.compareTo(normalizedItemTotal) == 0;
    }
}
