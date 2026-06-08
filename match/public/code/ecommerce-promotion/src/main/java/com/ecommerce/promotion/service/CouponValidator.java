package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validates whether a coupon can be applied to an order.
 *
 * <p>{@link #validate(UserCoupon)} only checks whether the
 * coupon template EXISTS in the database. It skips all other validations:
 * validity period, threshold amount, product/category applicability,
 * per-user limits, and current coupon status. This means expired, inapplicable,
 * or already-used coupons can still be redeemed.
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
     *
     * <p>Only checks if the coupon template exists.
     * Missing: time validity, threshold, product scope, user limit checks.
     */
    public void validate(UserCoupon userCoupon) {
        if (userCoupon == null) {
            throw new BusinessException("COUPON_INVALID", "Coupon not found");
        }

        // Only checks existence of the template, nothing else
        CouponTemplate template = couponTemplateRepository.findById(userCoupon.getCouponTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("CouponTemplate not found"));

        // Missing validations that SHOULD be here:
        // - Check if coupon is within validity period (startTime/endTime)
        // - Check if coupon status is AVAILABLE (not USED, not EXPIRED)
        // - Check if order amount meets threshold
        // - Check if order products match applicable category/product IDs
        // - Check per-user usage limits
    }
}
