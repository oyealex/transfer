package com.ecommerce.promotion.controller;

import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.promotion.dto.CouponClaimRequest;
import com.ecommerce.promotion.dto.PromotionCalculateRequest;
import com.ecommerce.promotion.dto.PromotionCalculateResponse;
import com.ecommerce.promotion.entity.CouponStatus;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.entity.UserCoupon;
import com.ecommerce.promotion.repository.CouponTemplateRepository;
import com.ecommerce.promotion.repository.UserCouponRepository;
import com.ecommerce.promotion.service.CouponService;
import com.ecommerce.promotion.service.PromotionCalculationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} for {@link PromotionController}.
 */
@WebMvcTest(PromotionController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("PromotionController")
class PromotionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CouponService couponService;

    @MockBean
    private UserCouponRepository userCouponRepository;

    @MockBean
    private CouponTemplateRepository couponTemplateRepository;

    @MockBean
    private PromotionCalculationService promotionCalculationService;

    // -----------------------------------------------------------------------
    // Claim Coupon tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/promotions/coupons/claim")
    class ClaimCoupon {

        private CouponClaimRequest request;
        private UserCoupon claimedCoupon;

        @BeforeEach
        void setUp() {
            request = new CouponClaimRequest();
            request.setCouponTemplateId(1L);

            claimedCoupon = new UserCoupon();
            claimedCoupon.setId(100L);
            claimedCoupon.setUserId(1L);
            claimedCoupon.setCouponTemplateId(1L);
            claimedCoupon.setCouponCode("CPN-TESTABC");
            claimedCoupon.setStatus(CouponStatus.AVAILABLE);
            claimedCoupon.setClaimedAt(LocalDateTime.now());
        }

        @Test
        @DisplayName("claimCoupon: returns 201 with claimed UserCoupon")
        void testClaimCoupon_returnsCreated() throws Exception {
            when(couponService.claim(anyLong(), anyLong())).thenReturn(claimedCoupon);

            mockMvc.perform(post("/api/v1/promotions/coupons/claim")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.couponTemplateId").value(1))
                    .andExpect(jsonPath("$.couponCode").value("CPN-TESTABC"))
                    .andExpect(jsonPath("$.status").value("AVAILABLE"));
        }

        @Test
        @DisplayName("claimCoupon: returns 400 when coupon is exhausted")
        void testClaimCoupon_exhausted_returns400() throws Exception {
            when(couponService.claim(anyLong(), anyLong()))
                    .thenThrow(new com.ecommerce.common.exception.BusinessException(
                            "COUPON_EXHAUSTED", "Coupon has been fully claimed"));

            mockMvc.perform(post("/api/v1/promotions/coupons/claim")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COUPON_EXHAUSTED"));
        }
    }

    // -----------------------------------------------------------------------
    // Get My Coupons tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/promotions/coupons/my")
    class GetMyCoupons {

        private UserCoupon userCoupon;
        private CouponTemplate template;

        @BeforeEach
        void setUp() {
            userCoupon = new UserCoupon();
            userCoupon.setId(10L);
            userCoupon.setUserId(1L);
            userCoupon.setCouponTemplateId(1L);
            userCoupon.setCouponCode("CPN-ABC123");
            userCoupon.setStatus(CouponStatus.AVAILABLE);

            template = new CouponTemplate();
            template.setId(1L);
            template.setName("10% Off");
            template.setType(CouponType.DISCOUNT);
            template.setDiscountValue(new BigDecimal("0.9"));
            template.setThresholdAmount(new BigDecimal("50.00"));
            template.setMaxDiscount(new BigDecimal("20.00"));
            template.setEndTime(LocalDateTime.now().plusDays(7));
        }

        @Test
        @DisplayName("getMyCoupons: returns list of user coupons with template details")
        void testGetMyCoupons_returnsList() throws Exception {
            when(userCouponRepository.findByUserId(1L)).thenReturn(List.of(userCoupon));
            when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

            mockMvc.perform(get("/api/v1/promotions/coupons/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].userCouponId").value(10))
                    .andExpect(jsonPath("$[0].couponCode").value("CPN-ABC123"))
                    .andExpect(jsonPath("$[0].name").value("10% Off"))
                    .andExpect(jsonPath("$[0].type").value("DISCOUNT"))
                    .andExpect(jsonPath("$[0].discountValue").value(0.9))
                    .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
        }

        @Test
        @DisplayName("getMyCoupons: returns empty list when no coupons")
        void testGetMyCoupons_emptyList() throws Exception {
            when(userCouponRepository.findByUserId(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/promotions/coupons/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("getMyCoupons: handles missing template gracefully — partial data")
        void testGetMyCoupons_missingTemplate_partialData() throws Exception {
            when(userCouponRepository.findByUserId(1L)).thenReturn(List.of(userCoupon));
            when(couponTemplateRepository.findById(1L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/promotions/coupons/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].userCouponId").value(10))
                    .andExpect(jsonPath("$[0].couponCode").value("CPN-ABC123"))
                    .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                    .andExpect(jsonPath("$[0].name").isEmpty());
        }

        @Test
        @DisplayName("getMyCoupons: returns multiple coupons")
        void testGetMyCoupons_multipleCoupons() throws Exception {
            UserCoupon coupon2 = new UserCoupon();
            coupon2.setId(20L);
            coupon2.setUserId(1L);
            coupon2.setCouponTemplateId(2L);
            coupon2.setCouponCode("CPN-DEF456");
            coupon2.setStatus(CouponStatus.USED);

            CouponTemplate template2 = new CouponTemplate();
            template2.setId(2L);
            template2.setName("$5 Off");
            template2.setType(CouponType.AMOUNT_OFF);
            template2.setDiscountValue(new BigDecimal("5.00"));
            template2.setEndTime(LocalDateTime.now().plusDays(3));

            when(userCouponRepository.findByUserId(1L)).thenReturn(List.of(userCoupon, coupon2));
            when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
            when(couponTemplateRepository.findById(2L)).thenReturn(Optional.of(template2));

            mockMvc.perform(get("/api/v1/promotions/coupons/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].userCouponId").value(10))
                    .andExpect(jsonPath("$[1].userCouponId").value(20))
                    .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                    .andExpect(jsonPath("$[1].status").value("USED"));
        }
    }

    // -----------------------------------------------------------------------
    // Calculate tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/promotions/calculate")
    class Calculate {

        private PromotionCalculateRequest request;
        private PromotionCalculateResponse response;

        @BeforeEach
        void setUp() {
            PromotionCalculateRequest.CalculateItem item =
                    new PromotionCalculateRequest.CalculateItem();
            item.setSkuId(1L);
            item.setPrice(new BigDecimal("100.00"));
            item.setQuantity(1);

            request = new PromotionCalculateRequest();
            request.setItems(List.of(item));
            request.setUserId(1L);
            request.setCouponIds(List.of(1L));

            response = new PromotionCalculateResponse();
            response.setItemTotal(new BigDecimal("100.00"));
            response.setMemberDiscount(new BigDecimal("5.00"));
            response.setFullReductionDiscount(new BigDecimal("10.00"));
            response.setCouponDiscount(new BigDecimal("68.00"));
            response.setTotalDiscount(new BigDecimal("83.00"));
            response.setFinalAmount(new BigDecimal("17.00"));
            response.setApplicableCoupons(List.of());
        }

        @Test
        @DisplayName("calculate: returns 200 with calculation breakdown")
        void testCalculate_returns200() throws Exception {
            when(promotionCalculationService.calculate(any(PromotionCalculateRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/promotions/calculate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemTotal").value(100.0))
                    .andExpect(jsonPath("$.memberDiscount").value(5.0))
                    .andExpect(jsonPath("$.fullReductionDiscount").value(10.0))
                    .andExpect(jsonPath("$.couponDiscount").value(68.0))
                    .andExpect(jsonPath("$.totalDiscount").value(83.0))
                    .andExpect(jsonPath("$.finalAmount").value(17.0));
        }

        @Test
        @DisplayName("calculate: sets userId when null")
        void testCalculate_nullUserId_isSet() throws Exception {
            request.setUserId(null);

            when(promotionCalculationService.calculate(any(PromotionCalculateRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/promotions/calculate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("calculate: validates request — 400 on empty items")
        void testCalculate_emptyItems_returns400() throws Exception {
            request.setItems(List.of());

            mockMvc.perform(post("/api/v1/promotions/calculate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("calculate: returns no-discount result")
        void testCalculate_noDiscounts() throws Exception {
            response.setMemberDiscount(BigDecimal.ZERO);
            response.setFullReductionDiscount(BigDecimal.ZERO);
            response.setCouponDiscount(BigDecimal.ZERO);
            response.setTotalDiscount(BigDecimal.ZERO);
            response.setFinalAmount(new BigDecimal("100.00"));

            when(promotionCalculationService.calculate(any(PromotionCalculateRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/promotions/calculate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalDiscount").value(0.0))
                    .andExpect(jsonPath("$.finalAmount").value(100.0));
        }
    }
}
