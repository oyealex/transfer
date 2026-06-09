package com.ecommerce.product;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot test application for the product module.
 * Required by {@code @WebMvcTest} and {@code @DataJpaTest} slice tests
 * since this is a library module without its own main class.
 */
@SpringBootApplication(scanBasePackages = "com.ecommerce.product.controller")
public class TestApplication {
}
