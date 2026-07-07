package com.ecommerce.loyalty.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fetches order-related data for the loyalty module.
 */
@Component
public class OrderDataFetcher {

    private final JdbcTemplate jdbcTemplate;

    public OrderDataFetcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the annual paid consumption amount for a user.
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
