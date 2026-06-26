package com.ecommerce.common.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Auto-configuration for the ecommerce-common module.
 * Enables JPA auditing and component scanning for the common package
 * so that other modules only need to depend on this module's artifact.
 */
@Configuration
@EnableJpaAuditing
@ComponentScan(basePackages = "com.ecommerce.common")
public class CommonAutoConfiguration {
}
