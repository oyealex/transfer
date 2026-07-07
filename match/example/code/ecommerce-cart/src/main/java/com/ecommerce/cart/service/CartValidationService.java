package com.ecommerce.cart.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.query.InventoryQueryService;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.product.query.StockSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for validating cart operations — SKU status and stock availability.
 */
@Service
public class CartValidationService {

    private static final Logger log = LoggerFactory.getLogger(CartValidationService.class);

    private static final String SKU_STATUS_ON_SHELF = "ON_SHELF";
    private static final int MAX_ITEM_TYPES = 100;
    private static final int MAX_QUANTITY = 999;

    private final ProductQueryService productQueryService;
    private final InventoryQueryService inventoryQueryService;

    public CartValidationService(ProductQueryService productQueryService,
                                  InventoryQueryService inventoryQueryService) {
        this.productQueryService = productQueryService;
        this.inventoryQueryService = inventoryQueryService;
    }

    /**
     * Validates that the SKU exists and is available for sale.
     *
     * @param skuId the SKU to validate
     * @return the validated SkuDto
     * @throws BusinessException if SKU is not available for sale
     */
    public SkuDto validateSku(Long skuId) {
        SkuDto sku = productQueryService.getSkuForSale(skuId);
        if (sku == null) {
            throw new ResourceNotFoundException("SKU", skuId);
        }
        if (!SKU_STATUS_ON_SHELF.equals(sku.getStatus())) {
            throw new BusinessException("SKU_NOT_AVAILABLE",
                    "SKU " + skuId + " is not available for sale, current status: " + sku.getStatus());
        }
        log.debug("SKU {} validated: status={}, price={}", skuId, sku.getStatus(), sku.getPrice());
        return sku;
    }

    /**
     * Validates that sufficient stock exists for the requested quantity.
     * Uses {@code >=} for boundary comparison (correct behavior).
     *
     * @param skuId    the SKU id
     * @param quantity the requested quantity
     * @throws BusinessException if insufficient stock
     */
    public void validateStock(Long skuId, int quantity) {
        StockSummaryDto stock = inventoryQueryService.getStockSummary(skuId);
        if (stock == null || stock.getAvailableStock() < quantity) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    "Insufficient stock for SKU " + skuId
                            + ": requested=" + quantity
                            + ", available=" + (stock != null ? stock.getAvailableStock() : 0));
        }
        log.debug("Stock validated for SKU {}: requested={}, available={}", skuId, quantity, stock.getAvailableStock());
    }

    /**
     * Validates that the quantity is within allowed range (1 to 999).
     *
     * @param quantity the quantity to validate
     * @throws BusinessException if quantity is out of range
     */
    public void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new BusinessException("INVALID_QUANTITY",
                    "Quantity must be between 1 and " + MAX_QUANTITY + ", got: " + quantity);
        }
    }

    /**
     * Validates that adding new item types does not exceed the maximum cart size.
     *
     * @param currentItemCount  current number of distinct items in cart
     * @param newItemTypesToAdd number of new item types being added
     * @throws BusinessException if the cart would exceed the max
     */
    public void validateCartSize(int currentItemCount, int newItemTypesToAdd) {
        if (currentItemCount + newItemTypesToAdd > MAX_ITEM_TYPES) {
            throw new BusinessException("CART_FULL",
                    "Cart can contain at most " + MAX_ITEM_TYPES + " distinct items. "
                            + "Current: " + currentItemCount + ", adding: " + newItemTypesToAdd);
        }
    }
}
