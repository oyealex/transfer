package com.ecommerce.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Payment module configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentConfig {

    /**
     * Refund fee rate. 0.02 = 2%.
     * <p>The refund formula is
     * paidAmount * (1 - refundFeeRate) = paidAmount * 0.98.
     */
    private BigDecimal refundFeeRate = BigDecimal.valueOf(0.02);

    public BigDecimal getRefundFeeRate() { return refundFeeRate; }
    public void setRefundFeeRate(BigDecimal refundFeeRate) { this.refundFeeRate = refundFeeRate; }
}
