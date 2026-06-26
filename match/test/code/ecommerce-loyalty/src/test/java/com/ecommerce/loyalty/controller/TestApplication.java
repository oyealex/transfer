package com.ecommerce.loyalty.controller;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for {@code @WebMvcTest} in the
 * ecommerce-loyalty module. The module has no {@code @SpringBootApplication}
 * of its own, so this test-scoped class provides one for test slices.
 */
@SpringBootApplication(scanBasePackages = "com.ecommerce.loyalty.controller")
public class TestApplication {
}
