package com.ecommerce.product.query;

import java.util.Collection;
import java.util.List;

/**
 * Cross-module query interface exposed by the product module.
 * Other modules (inventory, order, cart, promotion, etc.) use this interface
 * to query product data without depending on product JPA entities or repositories.
 *
 * <p>The product module provides the implementation; consumers inject this interface.
 */
public interface ProductQueryService {

    /**
     * Returns the SKU with the given id, or null if not found.
     */
    SkuDto getSku(Long skuId);

    /**
     * Returns the SKU if it is available for sale (ON_SHELF, not deleted).
     *
     * @param skuId the SKU id
     * @return the salable SKU
     * @throws com.ecommerce.common.exception.BusinessException if the SKU is not available for sale
     */
    SkuDto getSkuForSale(Long skuId);

    /**
     * Batch-query multiple SKUs by their ids.
     */
    List<SkuDto> listSkuByIds(Collection<Long> skuIds);

    /**
     * Returns a snapshot of the product at the current point in time,
     * used for order snapshots to preserve historical accuracy.
     */
    ProductSnapshotDto getProductSnapshot(Long skuId);
}
