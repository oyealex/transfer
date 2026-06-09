package com.ecommerce.order.query;

import com.ecommerce.order.dto.VerifyPurchaseResponse;

import java.math.BigDecimal;

/**
 * Cross-module query interface exposed by the order module.
 * Other modules (payment, review, logistics, loyalty, etc.) use this interface
 * to query order data without depending on order JPA entities or repositories.
 *
 * <p>The payment module MUST use this interface to query order information,
 * and MUST NOT access the order database directly.
 */
public interface OrderQueryService {

    /**
     * Query order details by order ID.
     *
     * @param orderId the order ID
     * @return the order DTO
     */
    OrderDto getOrder(Long orderId);

    /**
     * Query an order that is in a payable state (CREATED or PAYING).
     * Throws an exception if the order is not in a payable state.
     *
     * @param orderId the order ID
     * @return the order DTO
     */
    OrderDto getPayableOrder(Long orderId);

    /**
     * Verify whether a user has purchased and received (DELIVERED) a specific product.
     * Used by the review module to verify purchase eligibility before allowing reviews.
     *
     * @param userId    the user ID
     * @param productId the product (SPU) ID
     * @return verification result
     */
    VerifyPurchaseResponse verifyPurchase(Long userId, Long productId);

    /**
     * Get the payable amount for an order.
     * Used by the payment module to validate payment amounts.
     *
     * @param orderId the order ID
     * @return the payable amount
     */
    BigDecimal getOrderAmount(Long orderId);
}
