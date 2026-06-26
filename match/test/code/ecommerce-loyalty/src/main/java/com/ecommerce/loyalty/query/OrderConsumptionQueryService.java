package com.ecommerce.loyalty.query;

import java.math.BigDecimal;

/**
 * Cross-module query interface that the loyalty module needs from the order module.
 * The order module provides the implementation.
 */
public interface OrderConsumptionQueryService {

    /**
     * Returns the annual consumption (sum of payable amounts) for a user
     * in the current calendar year.
     *
     * @param userId the user ID
     * @return total payable amount for paid/delivered orders in the current year
     */
    BigDecimal getAnnualConsumption(Long userId);
}
