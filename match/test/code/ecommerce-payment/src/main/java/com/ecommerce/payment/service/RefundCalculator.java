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
     */
    public BigDecimal calculate(BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal feeRate = paymentConfig.getRefundFeeRate();
        BigDecimal refundFactor = BigDecimal.ONE.subtract(feeRate);

        BigDecimal refund = MonetaryUtil.multiply(paidAmount, refundFactor);

        log.debug("Refund calculated: paid={}, factor={}, refund={}",
                paidAmount, refundFactor, refund);
        return refund;
    }
}
