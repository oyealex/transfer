package com.ecommerce.product.query;

/**
 * Cross-module interface that defines what the product module needs from the inventory module.
 * The inventory module provides the implementation of this interface.
 *
 * <p>Product services use this interface to obtain stock information
 * from the inventory module.
 */
public interface InventoryQueryService {

    /**
     * Returns a stock summary for the given SKU, including available and reserved quantities.
     *
     * @param skuId the SKU id
     * @return the stock summary, or a default summary with zero stock if no data is available
     */
    StockSummaryDto getStockSummary(Long skuId);
}
