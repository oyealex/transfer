package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Validates whether a coupon can be applied to an order.
 */
@Service
public class CouponValidator {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponValidator(CouponTemplateRepository couponTemplateRepository,
                            UserCouponRepository userCouponRepository) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
    }

    /**
     * Validate that a coupon is applicable.
     * Checks: existence, status (AVAILABLE), validity period (startTime/endTime).
     */
    public void validate(UserCoupon userCoupon) {
        if (userCoupon == null) {
            throw new BusinessException("COUPON_INVALID", "Coupon not found");
        }

        // L2-04: Check coupon status — must be AVAILABLE
        if (userCoupon.getStatus() != CouponStatus.AVAILABLE) {
            throw new BusinessException("COUPON_EXPIRED",
                    "Coupon is not available, status: " + userCoupon.getStatus());
        }

        CouponTemplate template = couponTemplateRepository.findById(userCoupon.getCouponTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("CouponTemplate not found"));

        // L2-04: Check validity period (use system clock for testability)
        var now = com.ecommerce.common.test.SystemClockService.now();
        if (template.getStartTime() != null && now.isBefore(template.getStartTime())) {
            throw new BusinessException("COUPON_NOT_YET_VALID",
                    "Coupon is not yet valid, starts at " + template.getStartTime());
        }
        if (template.getEndTime() != null && now.isAfter(template.getEndTime())) {
            throw new BusinessException("COUPON_EXPIRED",
                    "Coupon has expired at " + template.getEndTime());
        }
    }
}
