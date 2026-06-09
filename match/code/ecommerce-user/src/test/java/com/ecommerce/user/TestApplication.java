package com.ecommerce.user;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application for {@code @WebMvcTest} in the
 * ecommerce-user module. The module has no {@code @SpringBootApplication}
 * of its own, so this test-scoped class provides one for test slices.
 *
 * <p>Uses {@code @SpringBootApplication} with an explicit scan package
 * to avoid picking up JPA entities/configuration that would trigger
 * DataSource auto-configuration.
 */
@SpringBootApplication(scanBasePackages = "com.ecommerce.user.controller")
public class TestApplication {
}
