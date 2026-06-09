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
     */
    public void validate(UserCoupon userCoupon) {
        if (userCoupon == null) {
            throw new BusinessException("COUPON_INVALID", "Coupon not found");
        }

        CouponTemplate template = couponTemplateRepository.findById(userCoupon.getCouponTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("CouponTemplate not found"));
    }
}
