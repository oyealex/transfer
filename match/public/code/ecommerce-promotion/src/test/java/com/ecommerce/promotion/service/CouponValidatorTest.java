package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CouponValidator}.
 *
 * <p>{@link CouponValidator#validate(UserCoupon)} only checks
 * whether the coupon template EXISTS in the database. It skips ALL other
 * validations: validity period, threshold amount, product/category applicability,
 * per-user limits, and current coupon status. This means expired, inapplicable,
 * or already-used coupons can still be redeemed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponValidator")
class CouponValidatorTest {

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponValidator couponValidator;

    // -----------------------------------------------------------------------
    // Shared test data
    // -----------------------------------------------------------------------

    private UserCoupon validUserCoupon;
    private CouponTemplate existingTemplate;
    private CouponTemplate expiredTemplate;

    @BeforeEach
    void setUp() {
        existingTemplate = new CouponTemplate();
        existingTemplate.setId(1L);
        existingTemplate.setName("Active Coupon");
        existingTemplate.setType(CouponType.AMOUNT_OFF);
        existingTemplate.setDiscountValue(new BigDecimal("10.00"));
        existingTemplate.setStatus("ACTIVE");
        existingTemplate.setStartTime(LocalDateTime.now().minusDays(7));
        existingTemplate.setEndTime(LocalDateTime.now().plusDays(7));

        expiredTemplate = new CouponTemplate();
        expiredTemplate.setId(2L);
        expiredTemplate.setName("Expired Coupon");
        expiredTemplate.setType(CouponType.DISCOUNT);
        expiredTemplate.setDiscountValue(new BigDecimal("0.8"));
        expiredTemplate.setStatus("ACTIVE");
        expiredTemplate.setStartTime(LocalDateTime.now().minusDays(30));
        expiredTemplate.setEndTime(LocalDateTime.now().minusDays(1));

        validUserCoupon = new UserCoupon();
        validUserCoupon.setId(10L);
        validUserCoupon.setUserId(1L);
        validUserCoupon.setCouponTemplateId(1L);
        validUserCoupon.setCouponCode("CPN-TEST001");
        validUserCoupon.setStatus(CouponStatus.AVAILABLE);
    }

    // -----------------------------------------------------------------------
    // Validate tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("testValidate_existingCoupon_returnsTrue: " +
                "only checks template existence, skips all other validations")
        void testValidate_existingCoupon_returnsTrue() {
            when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(existingTemplate));

            // Should NOT throw — template exists, and that is the only check performed
            assertThatCode(() -> couponValidator.validate(validUserCoupon))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("testValidate_nonExistentCoupon_returnsFalse: " +
                "throws ResourceNotFoundException when template does not exist")
        void testValidate_nonExistentCoupon_returnsFalse() {
            validUserCoupon.setCouponTemplateId(999L);
            when(couponTemplateRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponValidator.validate(validUserCoupon))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("CouponTemplate");
        }

        @Test
        @DisplayName("testValidate_expiredCoupon_stillReturnsTrue: " +
                "no time check, so expired coupons pass validation")
        void testValidate_expiredCoupon_stillReturnsTrue() {
            // Create a user coupon linked to an expired template
            UserCoupon expiredUserCoupon = new UserCoupon();
            expiredUserCoupon.setId(20L);
            expiredUserCoupon.setUserId(1L);
            expiredUserCoupon.setCouponTemplateId(2L);
            expiredUserCoupon.setCouponCode("CPN-EXPIRED");
            expiredUserCoupon.setStatus(CouponStatus.EXPIRED);

            when(couponTemplateRepository.findById(2L)).thenReturn(Optional.of(expiredTemplate));

            // Expired coupon still passes because no time check is done
            assertThatCode(() -> couponValidator.validate(expiredUserCoupon))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("testValidate_usedCoupon_stillReturnsTrue: " +
                "no status check, so USED coupons also pass")
        void testValidate_usedCoupon_stillReturnsTrue() {
            UserCoupon usedCoupon = new UserCoupon();
            usedCoupon.setId(30L);
            usedCoupon.setUserId(1L);
            usedCoupon.setCouponTemplateId(1L);
            usedCoupon.setCouponCode("CPN-USED");
            usedCoupon.setStatus(CouponStatus.USED);

            when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(existingTemplate));

            // USED coupon still passes validation
            assertThatCode(() -> couponValidator.validate(usedCoupon))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("testValidate_nullCoupon_throwsException")
        void testValidate_nullCoupon_throwsException() {
            assertThatThrownBy(() -> couponValidator.validate(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Coupon not found");
        }

        @Test
        @DisplayName("testValidate_couponWithFutureStartTime_stillPasses: " +
                "no time check, future-start coupons pass")
        void testValidate_couponWithFutureStartTime_stillPasses() {
            CouponTemplate futureTemplate = new CouponTemplate();
            futureTemplate.setId(3L);
            futureTemplate.setName("Future Coupon");
            futureTemplate.setType(CouponType.AMOUNT_OFF);
            futureTemplate.setDiscountValue(new BigDecimal("5.00"));
            futureTemplate.setStatus("ACTIVE");
            futureTemplate.setStartTime(LocalDateTime.now().plusDays(7));
            futureTemplate.setEndTime(LocalDateTime.now().plusDays(14));

            UserCoupon futureUserCoupon = new UserCoupon();
            futureUserCoupon.setId(40L);
            futureUserCoupon.setUserId(1L);
            futureUserCoupon.setCouponTemplateId(3L);
            futureUserCoupon.setCouponCode("CPN-FUTURE");
            futureUserCoupon.setStatus(CouponStatus.AVAILABLE);

            when(couponTemplateRepository.findById(3L)).thenReturn(Optional.of(futureTemplate));

            // No time validation — future coupon passes
            assertThatCode(() -> couponValidator.validate(futureUserCoupon))
                    .doesNotThrowAnyException();
        }
    }
}
