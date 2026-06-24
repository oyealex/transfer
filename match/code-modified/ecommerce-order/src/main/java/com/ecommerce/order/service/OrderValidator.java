package com.ecommerce.order.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.OrderValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Validates order-related data before processing.
 */
@Component
public class OrderValidator {

    private static final Logger log = LoggerFactory.getLogger(OrderValidator.class);

    /**
     * Validate that an order amount is positive.
     *
     * @param amount the amount to validate
     * @throws OrderValidationException if amount is null, zero, or negative
     */
    public void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderValidationException("Order amount must be positive, got: " + amount);
        }
        log.debug("Amount validated: {}", amount);
    }

    /**
     * Validate that a quantity is positive.
     */
    public void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new BusinessException("ORDER_INVALID_QUANTITY",
                    "Order item quantity cannot be negative, got: " + quantity);
        }
    }

    /**
     * Validate that an order has at least one item.
     */
    public void validateItemsCount(int count) {
        if (count <= 0) {
            throw new BusinessException("ORDER_EMPTY_ITEMS",
                    "Order must contain at least one item");
        }
    }
}
