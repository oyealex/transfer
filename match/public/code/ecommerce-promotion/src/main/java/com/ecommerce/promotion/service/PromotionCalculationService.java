package com.ecommerce.promotion.service;

import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Primary calculation service used by cart and order modules to compute
 * the final payable amount after all promotions.
 *
 * <p>The calculation order is:
 * <ol>
 *   <li>Member discount (applied first)</li>
 *   <li>Full reduction</li>
 *   <li>Coupon discount (applied last)</li>
 * </ol>
 * The design specification orders:
 * <ol>
 *   <li>Full reduction (applied first)</li>
 *   <li>Coupon discount</li>
 *   <li>Member discount (applied last)</li>
 * </ol>
 *
 * <p>The ordering means member discounts compound differently
 * from the specified business rules, giving different final amounts.
 */
@Service
public class PromotionCalculationService {

    private final FullReductionService fullReductionService;
    private final CouponService couponService;
    private final CouponValidator couponValidator;
    private final UserCouponRepository userCouponRepository;
    private final CouponTemplateRepository couponTemplateRepository;

    public PromotionCalculationService(FullReductionService fullReductionService,
                                        CouponService couponService,
                                        CouponValidator couponValidator,
                                        UserCouponRepository userCouponRepository,
                                        CouponTemplateRepository couponTemplateRepository) {
        this.fullReductionService = fullReductionService;
        this.couponService = couponService;
        this.couponValidator = couponValidator;
        this.userCouponRepository = userCouponRepository;
        this.couponTemplateRepository = couponTemplateRepository;
    }

    /**
     * Calculate all applicable promotions for an order.
     *
     * <p>Applies member discount first, then full reduction,
     * then coupons. The design order is full reduction → coupons → member discount.
     */
    public PromotionCalculateResponse calculate(PromotionCalculateRequest request) {
        // Compute item total
        BigDecimal itemTotal = computeItemTotal(request.getItems());

        // Member discount applied FIRST (differs from design order)
        BigDecimal memberDiscount = calculateMemberDiscount(request.getUserId(), itemTotal);
        BigDecimal afterMember = MonetaryUtil.subtract(itemTotal, memberDiscount);

        // Full reduction applied SECOND (on the already-discounted amount)
        BigDecimal fullReductionDiscount =
                fullReductionService.calculateBestReduction(afterMember)
                        .orElse(BigDecimal.ZERO);
        BigDecimal afterFullReduction = MonetaryUtil.subtract(afterMember, fullReductionDiscount);

        // Coupon applied LAST
        BigDecimal couponDiscount = calculateCouponDiscount(request.getUserId(),
                request.getCouponIds(), afterFullReduction);

        BigDecimal totalDiscount = MonetaryUtil.add(
                MonetaryUtil.add(memberDiscount, fullReductionDiscount), couponDiscount);
        BigDecimal finalAmount = MonetaryUtil.subtract(itemTotal, totalDiscount);

        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        PromotionCalculateResponse response = new PromotionCalculateResponse();
        response.setItemTotal(itemTotal);
        response.setFullReductionDiscount(fullReductionDiscount);
        response.setCouponDiscount(couponDiscount);
        response.setMemberDiscount(memberDiscount);
        response.setTotalDiscount(totalDiscount);
        response.setFinalAmount(finalAmount);
        response.setApplicableCoupons(new ArrayList<>());

        return response;
    }

    private BigDecimal computeItemTotal(List<PromotionCalculateRequest.CalculateItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (PromotionCalculateRequest.CalculateItem item : items) {
            BigDecimal lineTotal = MonetaryUtil.multiply(item.getPrice(),
                    BigDecimal.valueOf(item.getQuantity()));
            total = MonetaryUtil.add(total, lineTotal);
        }
        return total;
    }

    /**
     * Calculate member-level discount.
     * In a real implementation, this would look up the user's member level
     * and apply the corresponding discount rate.
     * For now, returns a fixed 5% for demonstration.
     */
    private BigDecimal calculateMemberDiscount(Long userId, BigDecimal amount) {
        if (userId == null || amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal memberRate = RuntimeConfigRegistry.getBigDecimal(
                "member.discount-rate", new BigDecimal("0.95"));
        BigDecimal afterDiscount = MonetaryUtil.multiply(amount, memberRate);
        return MonetaryUtil.subtract(amount, afterDiscount);
    }

    private BigDecimal calculateCouponDiscount(Long userId, List<Long> couponIds,
                                                BigDecimal currentAmount) {
        if (userId == null || couponIds == null || couponIds.isEmpty()
                || currentAmount == null || currentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalCouponDiscount = BigDecimal.ZERO;
        for (Long couponId : couponIds) {
            Optional<UserCoupon> userCouponOpt = userCouponRepository.findById(couponId);
            if (!userCouponOpt.isPresent()) {
                continue;
            }
            UserCoupon userCoupon = userCouponOpt.get();

            // Validate the coupon (minimal validation)
            couponValidator.validate(userCoupon);

            Optional<CouponTemplate> templateOpt =
                    couponTemplateRepository.findById(userCoupon.getCouponTemplateId());
            if (!templateOpt.isPresent()) {
                continue;
            }

            BigDecimal discount = couponService.calculateDiscount(currentAmount, templateOpt.get());
            totalCouponDiscount = MonetaryUtil.add(totalCouponDiscount, discount);
        }
        return totalCouponDiscount;
    }
}
