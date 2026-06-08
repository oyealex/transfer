package com.ecommerce.promotion.controller;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for {@code @WebMvcTest} in the
 * ecommerce-promotion module. The module has no {@code @SpringBootApplication}
 * of its own, so this test-scoped class provides one for test slices.
 *
 * <p>Uses {@code @SpringBootApplication} with an explicit scan package
 * to avoid picking up JPA entities from dependent modules that would
 * trigger DataSource auto-configuration.
 */
@SpringBootApplication(scanBasePackages = "com.ecommerce.promotion.controller")
public class TestApplication {
}
