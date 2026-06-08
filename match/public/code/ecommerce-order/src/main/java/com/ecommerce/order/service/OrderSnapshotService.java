package com.ecommerce.order.service;

import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.ProductSnapshotDto;
import com.ecommerce.user.query.AddressDto;
import com.ecommerce.user.query.UserQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for creating snapshots of mutable data at order time.
 *
 * <p>When an order is created, related data (product details, prices, shipping address)
 * must be captured as a snapshot to preserve historical accuracy. If a product price
 * changes or a user updates their address, existing orders should not be affected.
 *
 * <p>This service centralizes all snapshot creation logic so that other services
 * can simply request a snapshot without worrying about the JSON serialization details.
 */
@Service
public class OrderSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(OrderSnapshotService.class);

    private final ProductQueryService productQueryService;
    private final UserQueryService userQueryService;

    public OrderSnapshotService(ProductQueryService productQueryService,
                                 UserQueryService userQueryService) {
        this.productQueryService = productQueryService;
        this.userQueryService = userQueryService;
    }

    /**
     * Create a JSON snapshot of a product for the order item.
     * Captures the product data at the moment the order is placed so that
     * even if the product is later modified or deleted, the order remains accurate.
     *
     * @param skuId the SKU to snapshot
     * @return JSON string representing the product snapshot
     */
    public String createProductSnapshot(Long skuId) {
        try {
            ProductSnapshotDto snapshot = productQueryService.getProductSnapshot(skuId);
            if (snapshot == null) {
                log.warn("No product snapshot available for skuId={}, using minimal data", skuId);
                return buildMinimalProductSnapshot(skuId);
            }
            return buildProductSnapshotJson(snapshot);
        } catch (Exception e) {
            log.warn("Failed to create product snapshot for skuId={}: {}", skuId, e.getMessage());
            return buildMinimalProductSnapshot(skuId);
        }
    }

    /**
     * Create a JSON snapshot of a shipping address for the order.
     * Captures the address at order time so that even if the user changes
     * or deletes their address later, the order shipping address is preserved.
     *
     * @param userId the user ID
     * @return JSON string representing the address snapshot, or null if no address found
     */
    public String createAddressSnapshot(Long userId) {
        try {
            AddressDto address = userQueryService.getDefaultAddress(userId);
            if (address == null) {
                log.warn("No default address found for userId={}", userId);
                return null;
            }
            return buildAddressSnapshotJson(address);
        } catch (Exception e) {
            log.warn("Failed to create address snapshot for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Create a JSON snapshot of coupon IDs applied to the order.
     */
    public String createCouponSnapshot(java.util.List<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return null;
        }
        return couponIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    // ======================== Private helpers ========================

    private String buildProductSnapshotJson(ProductSnapshotDto snapshot) {
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
            for (java.util.Map.Entry<String, String> entry : snapshot.getSpecs().entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                        .append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    private String buildMinimalProductSnapshot(Long skuId) {
        return "{\"skuId\":" + skuId + ",\"note\":\"snapshot unavailable\"}";
    }

    private String buildAddressSnapshotJson(AddressDto address) {
        return "{" +
                "\"addressId\":" + address.getAddressId() + "," +
                "\"province\":\"" + escapeJson(address.getProvince()) + "\"," +
                "\"city\":\"" + escapeJson(address.getCity()) + "\"," +
                "\"district\":\"" + escapeJson(address.getDistrict()) + "\"," +
                "\"detail\":\"" + escapeJson(address.getDetail()) + "\"," +
                "\"receiverName\":\"" + escapeJson(address.getReceiverName()) + "\"," +
                "\"receiverPhone\":\"" + escapeJson(address.getReceiverPhone()) + "\"" +
                "}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
