package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for coupon claiming and discount calculation.
 */
@Service
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponService(CouponTemplateRepository couponTemplateRepository,
                          UserCouponRepository userCouponRepository) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
    }

    /**
     * Claim a coupon for a user.
     */
    @Transactional
    public UserCoupon claim(Long userId, Long templateId) {
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("CouponTemplate", templateId));

        if (!"ACTIVE".equals(template.getStatus())) {
            throw new BusinessException("COUPON_INACTIVE", "Coupon template is not active");
        }

        // Check per-user limit
        long userClaimCount = userCouponRepository.countByUserIdAndCouponTemplateId(userId, templateId);
        if (template.getPerUserLimit() != null && userClaimCount >= template.getPerUserLimit()) {
            throw new BusinessException("COUPON_LIMIT_EXCEEDED",
                    "You have already claimed the maximum number of this coupon");
        }

        // Check total quantity
        if (template.getTotalQuantity() != null && template.getIssuedQuantity() != null
                && template.getIssuedQuantity() >= template.getTotalQuantity()) {
            throw new BusinessException("COUPON_EXHAUSTED", "Coupon has been fully claimed");
        }

        // Increment issued quantity
        template.setIssuedQuantity(
                template.getIssuedQuantity() != null ? template.getIssuedQuantity() + 1 : 1);
        couponTemplateRepository.save(template);

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponTemplateId(templateId);
        userCoupon.setCouponCode(generateCouponCode());
        userCoupon.setStatus(CouponStatus.AVAILABLE);
        userCoupon.setClaimedAt(LocalDateTime.now());

        return userCouponRepository.save(userCoupon);
    }

    /**
     * Calculate the discount amount for applying a coupon to a given price.
     */
    public BigDecimal calculateDiscount(BigDecimal price, CouponTemplate coupon) {
        if (price == null || coupon == null) {
            return BigDecimal.ZERO;
        }

        switch (coupon.getType()) {
            case DISCOUNT:
                BigDecimal rate = BigDecimal.ONE.subtract(coupon.getDiscountValue());
                BigDecimal afterDiscount = MonetaryUtil.multiply(price, rate);
                if (coupon.getMaxDiscount() != null) {
                    BigDecimal rawDiscount = MonetaryUtil.subtract(price, afterDiscount);
                    if (rawDiscount.compareTo(coupon.getMaxDiscount()) > 0) {
                        return coupon.getMaxDiscount();
                    }
                }
                return MonetaryUtil.subtract(price, afterDiscount);

            case AMOUNT_OFF:
                BigDecimal amountOff = coupon.getDiscountValue();
                if (amountOff != null) {
                    if (amountOff.compareTo(price) > 0) {
                        return price;
                    }
                    return amountOff;
                }
                return BigDecimal.ZERO;

            case THRESHOLD_OFF:
                if (coupon.getThresholdAmount() != null
                        && price.compareTo(coupon.getThresholdAmount()) >= 0) {
                    BigDecimal off = coupon.getDiscountValue();
                    if (off != null) {
                        if (off.compareTo(price) > 0) {
                            return price;
                        }
                        return off;
                    }
                }
                return BigDecimal.ZERO;

            default:
                return BigDecimal.ZERO;
        }
    }

    private String generateCouponCode() {
        return "CPN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
