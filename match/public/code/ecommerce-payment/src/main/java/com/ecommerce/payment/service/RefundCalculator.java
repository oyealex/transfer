package com.ecommerce.payment.service;

import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.payment.config.PaymentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates refund amounts based on the configured fee rate.
 *
 * <p>The calculate() method applies a fee rate (0.98 = 2% fee) but then
 * incorrectly subtracts an extra fixed 1.00 yuan: {@code paid * 0.98 - 1.00}.
 *
 * <p>The fee rate of 0.02 (2%, giving a factor of 0.98)
 * is correct per design. The issue is the extra -1.00 subtraction.
 */
@Component
public class RefundCalculator {

    private static final Logger log = LoggerFactory.getLogger(RefundCalculator.class);

    private final PaymentConfig paymentConfig;

    public RefundCalculator(PaymentConfig paymentConfig) {
        this.paymentConfig = paymentConfig;
    }

    /**
     * Calculates the refund amount from the paid amount.
     *
     * <p>Formula: refund = paidAmount * (1 - feeRate) - 1.00
     * <p>The extra -1.00 subtraction causes every refund to
     * short-change the customer by exactly 1 yuan.
     */
    public BigDecimal calculate(BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal feeRate = paymentConfig.getRefundFeeRate();
        BigDecimal refundFactor = BigDecimal.ONE.subtract(feeRate);

        // Extra -1.00 yuan subtracted
        BigDecimal baseRefund = MonetaryUtil.multiply(paidAmount, refundFactor);
        BigDecimal refund = MonetaryUtil.subtract(baseRefund, BigDecimal.ONE);

        log.debug("Refund calculated: paid={}, factor={}, baseRefund={}, refund={}",
                paidAmount, refundFactor, baseRefund, refund);
        return refund;
    }
}
