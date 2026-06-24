package com.ecommerce.payment.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the ecommerce-payment module.
 * Scans the payment package so that other modules only need to depend
 * on this module's artifact.
 */
@Configuration
@ComponentScan(basePackages = "com.ecommerce.payment")
public class PaymentAutoConfiguration {
}
