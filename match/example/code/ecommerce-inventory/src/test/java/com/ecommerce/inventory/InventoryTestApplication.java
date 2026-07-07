package com.ecommerce.inventory;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Minimal Spring Boot test application for the ecommerce-inventory module.
 * Required by @WebMvcTest and @DataJpaTest slice tests
 * since this is a library module without its own main class.
 */
@SpringBootApplication(scanBasePackages = "com.ecommerce.inventory.controller")
public class InventoryTestApplication {
}
