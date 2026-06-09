package com.ecommerce.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Payment service standalone entry point.
 * Used only for development/testing of this module in isolation.
 * In production, this module is included as a dependency of the main application.
 */
@SpringBootApplication(scanBasePackages = {"com.ecommerce.payment", "com.ecommerce.common"})
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
