package com.ecommerce.promotion.controller;

import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.promotion.dto.CouponCreateRequest;
import com.ecommerce.promotion.dto.FullReductionCreateRequest;
import com.ecommerce.promotion.entity.CouponTemplate;
import com.ecommerce.promotion.entity.CouponType;
import com.ecommerce.promotion.entity.FullReductionActivity;
import com.ecommerce.promotion.entity.SeckillActivity;
import com.ecommerce.promotion.service.CouponTemplateService;
import com.ecommerce.promotion.service.FullReductionService;
import com.ecommerce.promotion.service.SeckillService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} for {@link AdminPromotionController}.
 */
@WebMvcTest(AdminPromotionController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("AdminPromotionController")
class AdminPromotionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CouponTemplateService couponTemplateService;

    @MockBean
    private FullReductionService fullReductionService;

    @MockBean
    private SeckillService seckillService;

    // -----------------------------------------------------------------------
    // Create Coupon tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/admin/promotions/coupons")
    class CreateCoupon {

        private CouponCreateRequest request;
        private CouponTemplate createdTemplate;

        @BeforeEach
        void setUp() {
            request = new CouponCreateRequest();
            request.setName("Summer Sale 20% Off");
            request.setType(CouponType.DISCOUNT);
            request.setDiscountValue(new BigDecimal("0.8"));
            request.setTotalQuantity(1000);
            request.setStartTime(LocalDateTime.now().minusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(7));

            createdTemplate = new CouponTemplate();
            createdTemplate.setId(1L);
            createdTemplate.setName("Summer Sale 20% Off");
            createdTemplate.setType(CouponType.DISCOUNT);
            createdTemplate.setDiscountValue(new BigDecimal("0.8"));
            createdTemplate.setTotalQuantity(1000);
            createdTemplate.setIssuedQuantity(0);
            createdTemplate.setStatus("ACTIVE");
        }

        @Test
        @DisplayName("createCoupon: returns 201 with created coupon template")
        void testCreateCoupon_returnsCreated() throws Exception {
            when(couponTemplateService.create(any(CouponCreateRequest.class)))
                    .thenReturn(createdTemplate);

            mockMvc.perform(post("/api/v1/admin/promotions/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Summer Sale 20% Off"))
                    .andExpect(jsonPath("$.type").value("DISCOUNT"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.issuedQuantity").value(0));
        }

        @Test
        @DisplayName("createCoupon: validates request body — 400 on missing name")
        void testCreateCoupon_missingName_returns400() throws Exception {
            request.setName(null);
            request.setType(null);

            mockMvc.perform(post("/api/v1/admin/promotions/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("createCoupon: creates AMOUNT_OFF coupon")
        void testCreateCoupon_amountOff() throws Exception {
            request.setType(CouponType.AMOUNT_OFF);
            request.setDiscountValue(new BigDecimal("20.00"));
            request.setName("$20 Off");

            createdTemplate.setType(CouponType.AMOUNT_OFF);
            createdTemplate.setName("$20 Off");
            createdTemplate.setDiscountValue(new BigDecimal("20.00"));

            when(couponTemplateService.create(any(CouponCreateRequest.class)))
                    .thenReturn(createdTemplate);

            mockMvc.perform(post("/api/v1/admin/promotions/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("AMOUNT_OFF"))
                    .andExpect(jsonPath("$.discountValue").value(20.0));
        }
    }

    // -----------------------------------------------------------------------
    // Create Full Reduction tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/admin/promotions/full-reductions")
    class CreateFullReduction {

        private FullReductionCreateRequest request;
        private FullReductionActivity createdActivity;

        @BeforeEach
        void setUp() {
            request = new FullReductionCreateRequest();
            request.setName("Spend 300 Get 30 Off");
            request.setThresholdAmount(new BigDecimal("300.00"));
            request.setReductionAmount(new BigDecimal("30.00"));
            request.setStartTime(LocalDateTime.now().minusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(7));

            createdActivity = new FullReductionActivity();
            createdActivity.setId(1L);
            createdActivity.setName("Spend 300 Get 30 Off");
            createdActivity.setThresholdAmount(new BigDecimal("300.00"));
            createdActivity.setReductionAmount(new BigDecimal("30.00"));
            createdActivity.setStatus("ACTIVE");
        }

        @Test
        @DisplayName("createFullReduction: returns 201 with created activity")
        void testCreateFullReduction_returnsCreated() throws Exception {
            when(fullReductionService.create(any(FullReductionCreateRequest.class)))
                    .thenReturn(createdActivity);

            mockMvc.perform(post("/api/v1/admin/promotions/full-reductions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Spend 300 Get 30 Off"))
                    .andExpect(jsonPath("$.thresholdAmount").value(300.0))
                    .andExpect(jsonPath("$.reductionAmount").value(30.0))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("createFullReduction: validates request — 400 on missing threshold")
        void testCreateFullReduction_missingThreshold_returns400() throws Exception {
            request.setThresholdAmount(null);

            mockMvc.perform(post("/api/v1/admin/promotions/full-reductions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("createFullReduction: validates request — 400 on missing name")
        void testCreateFullReduction_missingName_returns400() throws Exception {
            request.setName(null);

            mockMvc.perform(post("/api/v1/admin/promotions/full-reductions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // -----------------------------------------------------------------------
    // Create Seckill tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/admin/promotions/seckill")
    class CreateSeckill {

        private SeckillActivity request;
        private SeckillActivity createdActivity;

        @BeforeEach
        void setUp() {
            request = new SeckillActivity();
            request.setName("iPhone Flash Sale");
            request.setSkuId(100L);
            request.setSeckillPrice(new BigDecimal("999.00"));
            request.setStockQuantity(100);
            request.setPerUserLimit(1);
            request.setStartTime(LocalDateTime.now().minusHours(1));
            request.setEndTime(LocalDateTime.now().plusHours(1));

            createdActivity = new SeckillActivity();
            createdActivity.setId(1L);
            createdActivity.setName("iPhone Flash Sale");
            createdActivity.setSkuId(100L);
            createdActivity.setSeckillPrice(new BigDecimal("999.00"));
            createdActivity.setStockQuantity(100);
            createdActivity.setSoldQuantity(0);
            createdActivity.setPerUserLimit(1);
            createdActivity.setStartTime(LocalDateTime.now().minusHours(1));
            createdActivity.setEndTime(LocalDateTime.now().plusHours(1));
            createdActivity.setStatus("ACTIVE");
        }

        @Test
        @DisplayName("createSeckill: returns 201 with created activity")
        void testCreateSeckill_returnsCreated() throws Exception {
            when(seckillService.create(any(SeckillActivity.class)))
                    .thenReturn(createdActivity);

            mockMvc.perform(post("/api/v1/admin/promotions/seckill")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("iPhone Flash Sale"))
                    .andExpect(jsonPath("$.skuId").value(100))
                    .andExpect(jsonPath("$.seckillPrice").value(999.0))
                    .andExpect(jsonPath("$.stockQuantity").value(100))
                    .andExpect(jsonPath("$.soldQuantity").value(0))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("createSeckill: returns 400 when service throws ValidationException")
        void testCreateSeckill_invalidTimeRange_returns400() throws Exception {
            // Set invalid time range that the service will reject
            request.setStartTime(LocalDateTime.now());
            request.setEndTime(LocalDateTime.now().minusHours(1));

            when(seckillService.create(any(SeckillActivity.class)))
                    .thenThrow(new com.ecommerce.common.exception.ValidationException(
                            "endTime", "End time must be after start time"));

            mockMvc.perform(post("/api/v1/admin/promotions/seckill")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
}
