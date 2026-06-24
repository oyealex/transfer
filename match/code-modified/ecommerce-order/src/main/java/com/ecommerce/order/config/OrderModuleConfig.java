package com.ecommerce.order.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class for the order module.
 * Enables component scanning for the order package and scheduling
 * for timeout order cancellation.
 */
@Configuration
@ComponentScan(basePackages = "com.ecommerce.order")
@EnableScheduling
public class OrderModuleConfig {

    // Module configuration — beans are auto-detected via component scan.
    // @EnableScheduling enables the OrderTimeoutService to run periodic scans.
}
