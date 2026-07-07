package com.ecommerce.inventory.query;

import java.util.List;

/**
 * Cross-module query interface exposed by the inventory module.
 * Other modules (order, cart, product, etc.) use this interface
 * to query inventory data without depending on inventory JPA entities or repositories.
 *
 * <p>This interface intentionally reuses {@link com.ecommerce.product.query.StockSummaryDto}
 * as the return type for {@link #getStockSummary(Long)} so that a single implementation
 * can satisfy both this interface and {@link com.ecommerce.product.query.InventoryQueryService}.
 */
public interface InventoryQueryService {

    /**
     * Returns the stock summary for the given SKU across all warehouses.
     *
     * @param skuId the SKU id
     * @return the stock summary
     */
    com.ecommerce.product.query.StockSummaryDto getStockSummary(Long skuId);

    /**
     * Checks whether the requested quantity is available for the given SKU.
     * Returns true when available stock is strictly greater than the requested quantity.
     *
     * @param skuId    the SKU id
     * @param quantity the requested quantity
     * @return true if the quantity is available
     */
    boolean checkAvailability(Long skuId, int quantity);

    /**
     * Lists all warehouses that have available stock for the given SKU,
     * ordered by warehouse priority descending.
     *
     * @param skuId the SKU id
     * @return list of warehouse ids with available stock
     */
    List<Long> listAvailableWarehouses(Long skuId);
}
