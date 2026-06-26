package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link UserCoupon} entities.
 */
@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    /**
     * Find all coupons for a user.
     */
    List<UserCoupon> findByUserId(Long userId);

    /**
     * Find a specific coupon by user and coupon code.
     */
    Optional<UserCoupon> findByUserIdAndCouponCode(Long userId, String couponCode);

    /**
     * Find all coupons for a user with a specific status.
     */
    List<UserCoupon> findByUserIdAndStatus(Long userId, CouponStatus status);

    /**
     * Count how many coupons a user has claimed for a given template.
     */
    long countByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);
}
