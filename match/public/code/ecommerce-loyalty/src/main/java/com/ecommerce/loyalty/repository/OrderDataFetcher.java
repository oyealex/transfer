package com.ecommerce.loyalty.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fetches order-related data for the loyalty module.
 *
 * <p>This class bypasses the OrderQueryService interface
 * and queries the {@code orders} table directly via {@link JdbcTemplate}.
 * The intended design uses the OrderQueryService from the order module
 * (e.g. {@code OrderQueryService.getAnnualConsumption(userId)}) or the
 * sales statistics endpoint. Direct database access creates a tight coupling
 * to the order module's internal table schema.
 *
 * <p>Any change to the orders table (column rename, status enum change,
 * partition strategy) will silently break this component.
 */
@Component
public class OrderDataFetcher {

    private final JdbcTemplate jdbcTemplate;

    public OrderDataFetcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Queries the orders table directly instead of calling
     * OrderQueryService.
     *
     * @param userId the user ID
     * @return sum of payable_amount for paid orders in the current calendar year
     */
    public BigDecimal getAnnualConsumption(Long userId) {
        LocalDate startOfYear = LocalDate.now().withDayOfYear(1);
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(payable_amount), 0) FROM orders"
                + " WHERE user_id = ?"
                + " AND status IN ('PAID', 'SHIPPED', 'DELIVERED', 'COMPLETED')"
                + " AND paid_at >= ?",
                BigDecimal.class,
                userId,
                startOfYear);
    }
}
