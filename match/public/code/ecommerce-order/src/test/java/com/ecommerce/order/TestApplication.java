package com.ecommerce.order;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Test configuration for the ecommerce-order module.
 * Provides the {@code @SpringBootConfiguration} and {@code @EnableAutoConfiguration}
 * needed by Spring Boot test slices like {@code @DataJpaTest}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaAuditing
@EntityScan(basePackages = "com.ecommerce.order.entity")
public class TestApplication {
}
