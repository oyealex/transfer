package com.ecommerce.logistics.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the logistics module.
 * Enables component scanning for the logistics package.
 */
@Configuration
@ComponentScan(basePackages = "com.ecommerce.logistics")
public class LogisticsConfig {

    // Module configuration — beans are auto-detected via component scan.
}
