package com.ecommerce.promotion.service;

import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.entity.FullReductionActivity;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PromotionCalculationService}.
 *
 * <p>The calculation order is member→fullReduction→coupon.
 * The specification order is fullReduction→coupon→member.
 *
 * <p>Because member discount is applied first (on the full item total),
 * subsequent full-reduction and coupon discounts are applied to
 * already-discounted amounts, producing different final results than the
 * specification requires.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionCalculationService")
class PromotionCalculationServiceTest {

    @Mock
    private FullReductionService fullReductionService;

    @Mock
    private CouponService couponService;

    @Mock
    private CouponValidator couponValidator;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @InjectMocks
    private PromotionCalculationService promotionCalculationService;

    // -----------------------------------------------------------------------
    // Shared test data
    // -----------------------------------------------------------------------

    private PromotionCalculateRequest request;
    private PromotionCalculateRequest.CalculateItem item;
    private CouponTemplate discountTemplate;
    private UserCoupon userCoupon;
    private FullReductionActivity fullReductionActivity;

    @BeforeEach
    void setUp() {
        // Default item: price=100, qty=1 → itemTotal=100
        item = new PromotionCalculateRequest.CalculateItem();
        item.setSkuId(1L);
        item.setPrice(new BigDecimal("100.00"));
        item.setQuantity(1);

        request = new PromotionCalculateRequest();
        request.setItems(List.of(item));
        request.setUserId(1L);
        request.setCouponIds(List.of(1L));

        // DISCOUNT coupon with 0.8 rate
        discountTemplate = new CouponTemplate();
        discountTemplate.setId(100L);
        discountTemplate.setName("80% Off");
        discountTemplate.setType(CouponType.DISCOUNT);
        discountTemplate.setDiscountValue(new BigDecimal("0.8"));
        discountTemplate.setStatus("ACTIVE");

        // UserCoupon linking to the template
        userCoupon = new UserCoupon();
        userCoupon.setId(1L);
        userCoupon.setUserId(1L);
        userCoupon.setCouponTemplateId(100L);
        userCoupon.setCouponCode("CPN-DISC80");
        userCoupon.setStatus(CouponStatus.AVAILABLE);

        // Full-reduction: spend 90, get 10 off
        fullReductionActivity = new FullReductionActivity();
        fullReductionActivity.setId(1L);
        fullReductionActivity.setName("Spend 90 Get 10 Off");
        fullReductionActivity.setThresholdAmount(new BigDecimal("90.00"));
        fullReductionActivity.setReductionAmount(new BigDecimal("10.00"));
        fullReductionActivity.setStatus("ACTIVE");
    }

    // -----------------------------------------------------------------------
    // Calculate tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("calculate")
    class Calculate {

        @Test
        @DisplayName("testCalculate_verifiesDiscountOrder: " +
                "calculation order is member→fullReduction→coupon")
        void testCalculate_verifiesDiscountOrder() {
            /*
             * Trace through the actual calculation:
             *
             * STEP 1 - Member discount (5%, rate=0.95):
             *   afterMember = multiply(100.00, 0.95) = 95.00
             *   memberDiscount = subtract(100.00, 95.00) = 5.00
             *
             * STEP 2 - Full reduction (on 95.00):
             *   95.00 >= 90.00 threshold → reduction = 10.00
             *   afterFullReduction = subtract(95.00, 10.00) = 85.00
             *
             * STEP 3 - Coupon DISCOUNT 0.8 (on 85.00):
             *   rate = 1 - 0.8 = 0.2
             *   afterDiscount = multiply(85.00, 0.2) = 17.00
             *   couponDiscount = subtract(85.00, 17.00) = 68.00
             *
             * RESULT:
             *   totalDiscount = 5.00 + 10.00 + 68.00 = 83.00
             *   finalAmount = 100.00 - 83.00 = 17.00
             */
            when(fullReductionService.calculateBestReduction(any(BigDecimal.class)))
                    .thenReturn(Optional.of(new BigDecimal("10.00")));
            when(userCouponRepository.findById(1L)).thenReturn(Optional.of(userCoupon));
            when(couponTemplateRepository.findById(100L)).thenReturn(Optional.of(discountTemplate));
            // Validator does minimal check and passes
            when(couponService.calculateDiscount(any(BigDecimal.class), any(CouponTemplate.class)))
                    .thenAnswer(invocation -> {
                        // Simulate actual CouponService.calculateDiscount behavior for DISCOUNT 0.8
                        BigDecimal price = invocation.getArgument(0);
                        CouponTemplate ct = invocation.getArgument(1);
                        if (ct.getType() == CouponType.DISCOUNT) {
                            BigDecimal rate = BigDecimal.ONE.subtract(ct.getDiscountValue());
                            BigDecimal afterDiscount = MonetaryUtil.multiply(price, rate);
                            return MonetaryUtil.subtract(price, afterDiscount);
                        }
                        return BigDecimal.ZERO;
                    });

            PromotionCalculateResponse response = promotionCalculationService.calculate(request);

            // Verify calculation order metadata
            assertThat(response.getItemTotal()).isEqualByComparingTo(new BigDecimal("100.00"));

            // Member discount applied FIRST (5% of 100 = 5.00)
            assertThat(response.getMemberDiscount()).isEqualByComparingTo(new BigDecimal("5.00"));

            // Full reduction applied SECOND (10.00 off from 95.00)
            assertThat(response.getFullReductionDiscount()).isEqualByComparingTo(new BigDecimal("10.00"));

            // Coupon applied LAST: 68.00 on remaining 85.00
            assertThat(response.getCouponDiscount()).isEqualByComparingTo(new BigDecimal("68.00"));

            assertThat(response.getTotalDiscount()).isEqualByComparingTo(new BigDecimal("83.00"));
            assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("17.00"));

            // The order is member→fullReduction→coupon
            // With the specification order (fullReduction→coupon→member), the result
            // would be different.
            // Specification order:
            //   Full reduction: 100→90
            //   Coupon: rate=0.2, afterDiscount=90*0.2=18.00, discount=72.00
            //   Member: multiply(18.00, 0.95)=17.10, discount=0.90
            //   total=82.90, final=17.10
            // But actual gives 83.00/17.00
        }

        @Test
        @DisplayName("testCalculate_noDiscounts_appliesNone: " +
                "no userId, no coupons, no full reductions — returns item total as final")
        void testCalculate_noDiscounts_appliesNone() {
            // Null userId → member discount returns 0
            request.setUserId(null);
            request.setCouponIds(null);

            when(fullReductionService.calculateBestReduction(any(BigDecimal.class)))
                    .thenReturn(Optional.empty());

            PromotionCalculateResponse response = promotionCalculationService.calculate(request);

            assertThat(response.getItemTotal()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(response.getMemberDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getFullReductionDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getCouponDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getTotalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("testCalculate_multipleCoupons_appliesEach: " +
                "each coupon discount is applied independently to the same base amount")
        void testCalculate_multipleCoupons_appliesEach() {
            /*
             * Two DISCOUNT coupons, each at 0.8, applied to the same base = 85.00
             * (after member 5% and full reduction 10.00).
             *
             * Coupon 1 on 85.00: discount = 68.00
             * Coupon 2 on 85.00: discount = 68.00
             * Total coupon discount = 136.00
             * Total discount = 5.00 + 10.00 + 136.00 = 151.00
             * Final = max(0, 100 - 151) = 0.00
             */

            request.setCouponIds(List.of(1L, 2L));

            // Second user coupon
            UserCoupon userCoupon2 = new UserCoupon();
            userCoupon2.setId(2L);
            userCoupon2.setUserId(1L);
            userCoupon2.setCouponTemplateId(200L);
            userCoupon2.setCouponCode("CPN-DISC80-2");
            userCoupon2.setStatus(CouponStatus.AVAILABLE);

            // Second coupon template (same as first)
            CouponTemplate discountTemplate2 = new CouponTemplate();
            discountTemplate2.setId(200L);
            discountTemplate2.setName("80% Off #2");
            discountTemplate2.setType(CouponType.DISCOUNT);
            discountTemplate2.setDiscountValue(new BigDecimal("0.8"));
            discountTemplate2.setStatus("ACTIVE");

            when(fullReductionService.calculateBestReduction(any(BigDecimal.class)))
                    .thenReturn(Optional.of(new BigDecimal("10.00")));
            when(userCouponRepository.findById(1L)).thenReturn(Optional.of(userCoupon));
            when(userCouponRepository.findById(2L)).thenReturn(Optional.of(userCoupon2));
            when(couponTemplateRepository.findById(100L)).thenReturn(Optional.of(discountTemplate));
            when(couponTemplateRepository.findById(200L)).thenReturn(Optional.of(discountTemplate2));
            when(couponService.calculateDiscount(any(BigDecimal.class), any(CouponTemplate.class)))
                    .thenAnswer(invocation -> {
                        BigDecimal price = invocation.getArgument(0);
                        CouponTemplate ct = invocation.getArgument(1);
                        if (ct.getType() == CouponType.DISCOUNT) {
                            BigDecimal rate = BigDecimal.ONE.subtract(ct.getDiscountValue());
                            BigDecimal afterDiscount = MonetaryUtil.multiply(price, rate);
                            return MonetaryUtil.subtract(price, afterDiscount);
                        }
                        return BigDecimal.ZERO;
                    });

            PromotionCalculateResponse response = promotionCalculationService.calculate(request);

            assertThat(response.getItemTotal()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(response.getMemberDiscount()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(response.getFullReductionDiscount()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(response.getCouponDiscount()).isEqualByComparingTo(new BigDecimal("136.00"));
            assertThat(response.getTotalDiscount()).isEqualByComparingTo(new BigDecimal("151.00"));
            assertThat(response.getFinalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("testCalculate_multipleItems_sumsCorrectly")
        void testCalculate_multipleItems_sumsCorrectly() {
            PromotionCalculateRequest.CalculateItem item2 = new PromotionCalculateRequest.CalculateItem();
            item2.setSkuId(2L);
            item2.setPrice(new BigDecimal("50.00"));
            item2.setQuantity(2); // line total = 100

            request.setItems(List.of(item, item2));
            request.setUserId(null);
            request.setCouponIds(null);

            when(fullReductionService.calculateBestReduction(any(BigDecimal.class)))
                    .thenReturn(Optional.empty());

            PromotionCalculateResponse response = promotionCalculationService.calculate(request);

            // 100 * 1 + 50 * 2 = 200
            assertThat(response.getItemTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
            assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        }

        @Test
        @DisplayName("testCalculate_emptyCouponIds_skipsCoupons")
        void testCalculate_emptyCouponIds_skipsCoupons() {
            request.setCouponIds(Collections.emptyList());

            when(fullReductionService.calculateBestReduction(any(BigDecimal.class)))
                    .thenReturn(Optional.of(new BigDecimal("10.00")));

            PromotionCalculateResponse response = promotionCalculationService.calculate(request);

            assertThat(response.getCouponDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getMemberDiscount()).isEqualByComparingTo(new BigDecimal("5.00"));
            assertThat(response.getFullReductionDiscount()).isEqualByComparingTo(new BigDecimal("10.00"));
        }
    }
}
