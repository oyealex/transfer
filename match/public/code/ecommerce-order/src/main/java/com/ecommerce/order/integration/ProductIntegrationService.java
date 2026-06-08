package com.ecommerce.order.integration;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.ProductSnapshotDto;
import com.ecommerce.product.query.SkuDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralizes all product-related operations within the order module.
 * Wraps the ProductQueryService to provide order-specific product logic.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Validating that all requested SKUs are available for sale</li>
 *   <li>Fetching SKU price and metadata at order time</li>
 *   <li>Creating product snapshots for order history accuracy</li>
 *   <li>Calculating item totals from SKU prices and quantities</li>
 *   <li>Validating SKU availability across the order</li>
 * </ul>
 */
@Service
public class ProductIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ProductIntegrationService.class);

    private final ProductQueryService productQueryService;

    public ProductIntegrationService(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    /**
     * Validate and fetch SKU data for all items in an order request.
     * Throws BusinessException if any SKU is not available for sale.
     *
     * @param items the order items from the request
     * @return map of skuId -> SkuDto for all valid items
     */
    public Map<Long, SkuDto> validateAndFetchSkus(
            List<CreateOrderRequest.OrderItemRequest> items) {

        Map<Long, SkuDto> skuMap = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (CreateOrderRequest.OrderItemRequest item : items) {
            try {
                SkuDto sku = productQueryService.getSkuForSale(item.getSkuId());
                if (sku == null) {
                    errors.add("SKU not found: " + item.getSkuId());
                    continue;
                }
                skuMap.put(item.getSkuId(), sku);
            } catch (Exception e) {
                errors.add("SKU " + item.getSkuId() + ": " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException("PRODUCT_VALIDATION_FAILED",
                    "One or more products are not available: " + String.join("; ", errors));
        }

        log.debug("Validated {} SKUs for order", skuMap.size());
        return skuMap;
    }

    /**
     * Fetch SKU data for all items (non-validating, for queries/reconciliation).
     */
    public Map<Long, SkuDto> fetchSkus(Set<Long> skuIds) {
        Map<Long, SkuDto> result = new LinkedHashMap<>();
        for (Long skuId : skuIds) {
            try {
                SkuDto sku = productQueryService.getSku(skuId);
                if (sku != null) {
                    result.put(skuId, sku);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch SKU {}: {}", skuId, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Calculate the total item amount from SKU data and quantities.
     */
    public BigDecimal calculateItemTotal(Map<Long, SkuDto> skuMap,
                                          Map<Long, Integer> quantities) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, SkuDto> entry : skuMap.entrySet()) {
            Long skuId = entry.getKey();
            SkuDto sku = entry.getValue();
            Integer qty = quantities.getOrDefault(skuId, 0);
            if (qty > 0 && sku.getPrice() != null) {
                BigDecimal lineTotal = com.ecommerce.common.money.MonetaryUtil.multiply(
                        sku.getPrice(), BigDecimal.valueOf(qty));
                total = com.ecommerce.common.money.MonetaryUtil.add(total, lineTotal);
            }
        }
        log.debug("Calculated item total: {}", total);
        return total;
    }

    /**
     * Create a product snapshot for an order item.
     *
     * @param skuId the SKU to snapshot
     * @return JSON string representation
     */
    public String createSnapshot(Long skuId) {
        try {
            ProductSnapshotDto snapshot = productQueryService.getProductSnapshot(skuId);
            if (snapshot == null) {
                return buildFallbackSnapshot(skuId);
            }
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"skuId\":").append(snapshot.getSkuId()).append(",");
            sb.append("\"name\":\"").append(escapeJson(snapshot.getName())).append("\",");
            sb.append("\"price\":").append(snapshot.getPrice());
            if (snapshot.getImage() != null && !snapshot.getImage().isEmpty()) {
                sb.append(",\"image\":\"").append(escapeJson(snapshot.getImage())).append("\"");
            }
            if (snapshot.getSpecs() != null && !snapshot.getSpecs().isEmpty()) {
                sb.append(",\"specs\":{");
                boolean first = true;
                for (Map.Entry<String, String> spec : snapshot.getSpecs().entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(escapeJson(spec.getKey()))
                            .append("\":\"").append(escapeJson(spec.getValue())).append("\"");
                    first = false;
                }
                sb.append("}");
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to create product snapshot for skuId={}: {}", skuId, e.getMessage());
            return buildFallbackSnapshot(skuId);
        }
    }

    /**
     * Batch fetch SKU data for a list of SKU IDs.
     */
    public List<SkuDto> batchGetSkus(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return productQueryService.listSkuByIds(skuIds);
        } catch (Exception e) {
            log.warn("Failed to batch fetch SKUs: {}", e.getMessage());
            return skuIds.stream()
                    .map(id -> {
                        try { return productQueryService.getSku(id); }
                        catch (Exception ex) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Check if a SKU exists and is ON_SHELF.
     */
    public boolean isSkuAvailable(Long skuId) {
        try {
            SkuDto sku = productQueryService.getSkuForSale(skuId);
            return sku != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildFallbackSnapshot(Long skuId) {
        return "{\"skuId\":" + skuId + ",\"note\":\"snapshot_unavailable\"}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
