package com.ecommerce.order.integration;

import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.service.PromotionCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralizes all coupon/promotion integration within the order module.
 *
 * <p>This service wraps the PromotionCalculationService from the promotion module
 * and provides order-specific promotion logic, including:
 * <ul>
 *   <li>Building calculation requests from order data</li>
 *   <li>Handling promotion calculation errors gracefully</li>
 *   <li>Extracting discount breakdowns</li>
 *   <li>Validating coupon applicability to orders</li>
 * </ul>
 */
@Service
public class CouponIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(CouponIntegrationService.class);

    private final PromotionCalculationService promotionCalculationService;

    public CouponIntegrationService(PromotionCalculationService promotionCalculationService) {
        this.promotionCalculationService = promotionCalculationService;
    }

    /**
     * Calculate all applicable promotions for an order.
     * Builds the calculation request from order data and delegates to the promotion module.
     *
     * @param userId    the user ID
     * @param request   the order creation request
     * @param skuPrices map of skuId -> unit price
     * @param skuQtys   map of skuId -> quantity
     * @return the promotion calculation response
     */
    public PromotionCalculateResponse calculatePromotions(
            Long userId,
            CreateOrderRequest request,
            java.util.Map<Long, BigDecimal> skuPrices,
            java.util.Map<Long, Integer> skuQtys) {

        try {
            List<PromotionCalculateRequest.CalculateItem> calcItems = new ArrayList<>();
            for (CreateOrderRequest.OrderItemRequest item : request.getItems()) {
                PromotionCalculateRequest.CalculateItem calcItem =
                        new PromotionCalculateRequest.CalculateItem();
                calcItem.setSkuId(item.getSkuId());
                calcItem.setPrice(skuPrices.getOrDefault(item.getSkuId(), BigDecimal.ZERO));
                calcItem.setQuantity(item.getQuantity());
                calcItems.add(calcItem);
            }

            PromotionCalculateRequest calcRequest = new PromotionCalculateRequest();
            calcRequest.setUserId(userId);
            calcRequest.setItems(calcItems);
            calcRequest.setCouponIds(request.getCouponIds() != null
                    ? request.getCouponIds()
                    : Collections.emptyList());

            PromotionCalculateResponse response = promotionCalculationService.calculate(calcRequest);
            log.info("Promotion calculation: itemTotal={}, discountTotal={}, finalAmount={}",
                    response.getItemTotal(), response.getTotalDiscount(), response.getFinalAmount());
            return response;

        } catch (Exception e) {
            log.warn("Failed to calculate promotions, defaulting to zero discount: {}",
                    e.getMessage());
            PromotionCalculateResponse fallback = new PromotionCalculateResponse();
            fallback.setItemTotal(BigDecimal.ZERO);
            fallback.setFullReductionDiscount(BigDecimal.ZERO);
            fallback.setCouponDiscount(BigDecimal.ZERO);
            fallback.setMemberDiscount(BigDecimal.ZERO);
            fallback.setTotalDiscount(BigDecimal.ZERO);
            fallback.setFinalAmount(BigDecimal.ZERO);
            fallback.setApplicableCoupons(Collections.emptyList());
            return fallback;
        }
    }

    /**
     * Get the total discount from a promotion calculation response.
     * Convenience method that sums all discount types.
     */
    public BigDecimal getTotalDiscount(PromotionCalculateResponse response) {
        if (response == null) return BigDecimal.ZERO;
        if (response.getTotalDiscount() != null) return response.getTotalDiscount();

        BigDecimal total = BigDecimal.ZERO;
        if (response.getFullReductionDiscount() != null) {
            total = MonetaryUtil.add(total, response.getFullReductionDiscount());
        }
        if (response.getCouponDiscount() != null) {
            total = MonetaryUtil.add(total, response.getCouponDiscount());
        }
        if (response.getMemberDiscount() != null) {
            total = MonetaryUtil.add(total, response.getMemberDiscount());
        }
        return total;
    }

    /**
     * Validate that the coupons in the request are applicable to the order.
     * Returns a list of validation error messages, or empty list if all valid.
     */
    public List<String> validateCoupons(CreateOrderRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.getCouponIds() == null || request.getCouponIds().isEmpty()) {
            return errors;
        }

        // Basic validation: coupon IDs should be positive
        for (Long couponId : request.getCouponIds()) {
            if (couponId == null || couponId <= 0) {
                errors.add("Invalid coupon ID: " + couponId);
            }
        }

        // Check for duplicate coupon IDs
        List<Long> distinctIds = request.getCouponIds().stream()
                .distinct().collect(Collectors.toList());
        if (distinctIds.size() != request.getCouponIds().size()) {
            errors.add("Duplicate coupon IDs are not allowed");
        }

        // Maximum 5 coupons per order
        if (request.getCouponIds().size() > 5) {
            errors.add("Maximum 5 coupons per order, got " + request.getCouponIds().size());
        }

        return errors;
    }

    /**
     * Build a coupon breakdown for display in the order response.
     */
    public String buildCouponBreakdown(PromotionCalculateResponse response) {
        if (response == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"fullReductionDiscount\":").append(
                response.getFullReductionDiscount() != null
                        ? response.getFullReductionDiscount().toString() : "0");
        sb.append(",\"couponDiscount\":").append(
                response.getCouponDiscount() != null
                        ? response.getCouponDiscount().toString() : "0");
        sb.append(",\"memberDiscount\":").append(
                response.getMemberDiscount() != null
                        ? response.getMemberDiscount().toString() : "0");
        sb.append(",\"totalDiscount\":").append(
                response.getTotalDiscount() != null
                        ? response.getTotalDiscount().toString() : "0");
        sb.append("}");
        return sb.toString();
    }
}
