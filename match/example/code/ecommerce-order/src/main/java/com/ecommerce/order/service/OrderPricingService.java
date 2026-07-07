package com.ecommerce.order.service;

import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.PricingBreakdownResponse;
import com.ecommerce.order.integration.CouponIntegrationService;
import com.ecommerce.order.integration.LoyaltyIntegrationService;
import com.ecommerce.order.integration.ProductIntegrationService;
import com.ecommerce.product.query.SkuDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Comprehensive order pricing calculation service.
 *
 * <p>This is the single entry point for all price calculations in the order module.
 * It orchestrates the full pricing pipeline:
 * <ol>
 *   <li>Fetch and validate SKU prices</li>
 *   <li>Calculate item totals</li>
 *   <li>Calculate shipping and packaging fees</li>
 *   <li>Apply promotions and discounts</li>
 *   <li>Apply loyalty points deduction</li>
 *   <li>Compute final payable amount</li>
 * </ol>
 *
 * <p>This service provides the pricing breakdown DTO used by both the
 * create-order flow and the price-estimation (cart preview) flow.
 */
@Service
public class OrderPricingService {

    private static final Logger log = LoggerFactory.getLogger(OrderPricingService.class);

    private final ProductIntegrationService productIntegration;
    private final CouponIntegrationService couponIntegration;
    private final LoyaltyIntegrationService loyaltyIntegration;
    private final OrderTotalCalculator totalCalculator;

    public OrderPricingService(ProductIntegrationService productIntegration,
                                CouponIntegrationService couponIntegration,
                                LoyaltyIntegrationService loyaltyIntegration,
                                OrderTotalCalculator totalCalculator) {
        this.productIntegration = productIntegration;
        this.couponIntegration = couponIntegration;
        this.loyaltyIntegration = loyaltyIntegration;
        this.totalCalculator = totalCalculator;
    }

    /**
     * Calculate a full pricing breakdown for an order.
     * Used for preview/estimation before creating the order.
     *
     * @param userId  the user ID
     * @param request the order request
     * @return detailed pricing breakdown
     */
    public PricingBreakdownResponse calculatePricingBreakdown(Long userId,
                                                               CreateOrderRequest request) {
        log.info("Calculating pricing breakdown for userId={}, itemsCount={}",
                userId, request.getItems().size());

        // Step 1: Fetch and validate SKUs
        Map<Long, SkuDto> skuMap = productIntegration.validateAndFetchSkus(request.getItems());
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CreateOrderRequest.OrderItemRequest item : request.getItems()) {
            quantities.put(item.getSkuId(), item.getQuantity());
        }

        // Step 2: Calculate item total
        BigDecimal itemTotal = productIntegration.calculateItemTotal(skuMap, quantities);

        // Step 3: Calculate fees
        BigDecimal shippingFee = totalCalculator.calculateShippingFee(itemTotal);
        BigDecimal packagingFee = totalCalculator.calculatePackagingFee(request.getItems().size());

        String shippingNote = shippingFee.compareTo(BigDecimal.ZERO) == 0
                ? "Free (order amount >= 199.00)"
                : "Standard shipping fee";

        // Step 4: Calculate promotions
        com.ecommerce.promotion.dto.PromotionCalculateResponse promoResult =
                couponIntegration.calculatePromotions(userId, request,
                        skuMap.entrySet().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> e.getValue().getPrice())),
                        quantities);

        // Step 5: Calculate points deduction
        BigDecimal subtotalAfterDiscount = MonetaryUtil.subtract(itemTotal,
                promoResult.getTotalDiscount());
        subtotalAfterDiscount = MonetaryUtil.add(subtotalAfterDiscount, packagingFee);

        LoyaltyIntegrationService.PointsCalculationResult pointsResult =
                loyaltyIntegration.calculatePointsDeduction(
                        userId, subtotalAfterDiscount, request.getRedeemPoints());

        // Step 6: Calculate final payable
        BigDecimal payableAmount = totalCalculator.calculate(
                itemTotal, shippingFee, packagingFee,
                promoResult.getTotalDiscount(), pointsResult.getAmount());

        // Build response
        PricingBreakdownResponse resp = new PricingBreakdownResponse();
        resp.setItemTotal(itemTotal);
        resp.setItemCount(request.getItems().size());
        resp.setShippingFee(shippingFee);
        resp.setShippingNote(shippingNote);
        resp.setPackagingFee(packagingFee);
        resp.setFullReductionDiscount(promoResult.getFullReductionDiscount());
        resp.setCouponDiscount(promoResult.getCouponDiscount());
        resp.setMemberDiscount(promoResult.getMemberDiscount());
        resp.setTotalDiscount(promoResult.getTotalDiscount());
        resp.setRedeemedPoints(pointsResult.getPoints());
        resp.setPointsDeductionAmount(pointsResult.getAmount());

        // Subtotal breakdown
        resp.setSubtotalBeforeDiscount(MonetaryUtil.add(itemTotal, packagingFee));
        resp.setSubtotalAfterDiscount(subtotalAfterDiscount);
        resp.setPayableAmount(payableAmount);

        // Build formula string
        StringBuilder formula = new StringBuilder();
        formula.append(itemTotal).append(" (items)");
        formula.append(" + ").append(shippingFee).append(" (shipping)");
        formula.append(" + ").append(packagingFee).append(" (packaging)");
        formula.append(" - ").append(promoResult.getTotalDiscount()).append(" (discount)");
        formula.append(" - ").append(pointsResult.getAmount()).append(" (points)");
        formula.append(" = ").append(payableAmount);
        resp.setCalculationFormula(formula.toString());

        resp.setCalculationNote(
                "Note: shipping fee is displayed but may not be included in calculation. "
                        + "Promotion discount order may affect the result. "
                        + "Points cap: 10,000 points (100 yuan), max 50% of order amount.");

        log.info("Pricing breakdown: itemTotal={}, shippingFee={}, packagingFee={}, "
                        + "discount={}, points={}, payable={}",
                itemTotal, shippingFee, packagingFee,
                promoResult.getTotalDiscount(), pointsResult.getAmount(), payableAmount);

        return resp;
    }

    /**
     * Quick estimation: calculate payable amount without full breakdown.
     */
    public BigDecimal estimatePayableAmount(Long userId, CreateOrderRequest request) {
        PricingBreakdownResponse breakdown = calculatePricingBreakdown(userId, request);
        return breakdown.getPayableAmount();
    }

    /**
     * Calculate the non-discounted subtotal (items + shipping + packaging).
     */
    public BigDecimal calculateSubtotalBeforeDiscount(CreateOrderRequest request) {
        Map<Long, SkuDto> skuMap = productIntegration.validateAndFetchSkus(request.getItems());
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CreateOrderRequest.OrderItemRequest item : request.getItems()) {
            quantities.put(item.getSkuId(), item.getQuantity());
        }

        BigDecimal itemTotal = productIntegration.calculateItemTotal(skuMap, quantities);
        BigDecimal shippingFee = totalCalculator.calculateShippingFee(itemTotal);
        BigDecimal packagingFee = totalCalculator.calculatePackagingFee(request.getItems().size());

        return MonetaryUtil.add(MonetaryUtil.add(itemTotal, shippingFee), packagingFee);
    }
}
