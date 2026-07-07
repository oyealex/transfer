package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.common.test.RuntimeConfigRegistry;
import com.ecommerce.common.test.SystemClockService;
import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Primary calculation service used by cart and order modules to compute
 * the final payable amount after all promotions.
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
     */
    public PromotionCalculateResponse calculate(PromotionCalculateRequest request) {
        // Compute item total
        BigDecimal itemTotal = computeItemTotal(request.getItems());

        // L3-03: Apply full reduction FIRST (on full item total)
        BigDecimal fullReductionDiscount =
                fullReductionService.calculateBestReduction(itemTotal)
                        .orElse(BigDecimal.ZERO);
        BigDecimal afterFullReduction = MonetaryUtil.subtract(itemTotal, fullReductionDiscount);

        // Apply coupon discount SECOND (on amount after full reduction)
        BigDecimal couponDiscount = calculateCouponDiscount(request.getUserId(),
                request.getCouponIds(), afterFullReduction);
        BigDecimal afterCoupon = MonetaryUtil.subtract(afterFullReduction, couponDiscount);

        // Apply member-level discount LAST (on amount after full reduction + coupon)
        BigDecimal memberDiscount = calculateMemberDiscount(request.getUserId(), afterCoupon);

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
                "member.discount-rate", BigDecimal.ONE);
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
            if (couponId == null) {
                throw new BusinessException("COUPON_INVALID", "Coupon ID is required");
            }
            Optional<UserCoupon> userCouponOpt = userCouponRepository.findById(couponId);
            if (!userCouponOpt.isPresent()) {
                rejectTemplateIfExpired(couponId);
                throw new BusinessException("COUPON_INVALID",
                        "Coupon not found or not claimed: " + couponId);
            }
            UserCoupon userCoupon = userCouponOpt.get();
            if (!userId.equals(userCoupon.getUserId())) {
                throw new BusinessException("COUPON_NOT_OWNED",
                        "Coupon does not belong to current user: " + couponId);
            }

            couponValidator.validate(userCoupon);

            Optional<CouponTemplate> templateOpt =
                    couponTemplateRepository.findById(userCoupon.getCouponTemplateId());
            if (!templateOpt.isPresent()) {
                throw new BusinessException("COUPON_INVALID",
                        "Coupon template not found: " + userCoupon.getCouponTemplateId());
            }

            BigDecimal discount = couponService.calculateDiscount(currentAmount, templateOpt.get());
            totalCouponDiscount = MonetaryUtil.add(totalCouponDiscount, discount);
        }
        return totalCouponDiscount;
    }

    private void rejectTemplateIfExpired(Long templateId) {
        couponTemplateRepository.findById(templateId).ifPresent(template -> {
            LocalDateTime now = SystemClockService.now();
            if (template.getStartTime() != null && now.isBefore(template.getStartTime())) {
                throw new BusinessException("COUPON_NOT_YET_VALID",
                        "Coupon is not yet valid, starts at " + template.getStartTime());
            }
            if (template.getEndTime() != null && now.isAfter(template.getEndTime())) {
                throw new BusinessException("COUPON_EXPIRED",
                        "Coupon has expired at " + template.getEndTime());
            }
        });
    }
}
