package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.promotion.entity.SeckillActivity;
import com.ecommerce.promotion.repository.SeckillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SeckillService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SeckillService")
class SeckillServiceTest {

    @Mock
    private SeckillRepository seckillRepository;

    @InjectMocks
    private SeckillService seckillService;

    @Captor
    private ArgumentCaptor<SeckillActivity> activityCaptor;

    // -----------------------------------------------------------------------
    // Shared test data
    // -----------------------------------------------------------------------

    private SeckillActivity activity;

    @BeforeEach
    void setUp() {
        activity = new SeckillActivity();
        activity.setId(1L);
        activity.setName("iPhone Flash Sale");
        activity.setSkuId(100L);
        activity.setSeckillPrice(new BigDecimal("999.00"));
        activity.setStockQuantity(100);
        activity.setSoldQuantity(0);
        activity.setPerUserLimit(1);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setStatus("ACTIVE");
    }

    // -----------------------------------------------------------------------
    // Create tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("create: creates a seckill activity with default values")
        void testCreate_seckillActivity() {
            when(seckillRepository.save(any(SeckillActivity.class))).thenReturn(activity);

            SeckillActivity result = seckillService.create(activity);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("iPhone Flash Sale");
            assertThat(result.getSeckillPrice()).isEqualByComparingTo(new BigDecimal("999.00"));
            assertThat(result.getStatus()).isEqualTo("ACTIVE");

            verify(seckillRepository).save(activityCaptor.capture());
            SeckillActivity saved = activityCaptor.getValue();
            assertThat(saved.getSoldQuantity()).isEqualTo(0);
            assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("create: sets soldQuantity to 0 and status to ACTIVE")
        void testCreate_setsDefaults() {
            activity.setSoldQuantity(10); // input ignored
            activity.setStatus("INACTIVE"); // input ignored

            when(seckillRepository.save(any(SeckillActivity.class))).thenReturn(activity);

            seckillService.create(activity);

            verify(seckillRepository).save(activityCaptor.capture());
            assertThat(activityCaptor.getValue().getSoldQuantity()).isEqualTo(0);
            assertThat(activityCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("create: rejects invalid time range — endTime not after startTime")
        void testCreate_invalidTimeRange() {
            activity.setStartTime(LocalDateTime.now());
            activity.setEndTime(LocalDateTime.now().minusHours(1));

            assertThatThrownBy(() -> seckillService.create(activity))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("End time must be after start time");
        }

        @Test
        @DisplayName("create: accepts null time range without validation")
        void testCreate_nullTimeRange() {
            activity.setStartTime(null);
            activity.setEndTime(null);

            when(seckillRepository.save(any(SeckillActivity.class))).thenReturn(activity);

            // Should not throw — the null guard in create means the validation is skipped
            SeckillActivity result = seckillService.create(activity);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("create: accepts null endTime with non-null startTime")
        void testCreate_nullEndTime() {
            activity.setStartTime(LocalDateTime.now());
            activity.setEndTime(null);

            when(seckillRepository.save(any(SeckillActivity.class))).thenReturn(activity);

            SeckillActivity result = seckillService.create(activity);
            assertThat(result).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // validateSeckill tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("validateSeckill")
    class ValidateSeckill {

        @Test
        @DisplayName("validateSeckill: returns activity when within time window and stock available")
        void testValidateSeckill_active() {
            when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE"))
                    .thenReturn(Optional.of(activity));

            SeckillActivity result = seckillService.validateSeckill(100L);

            assertThat(result).isNotNull();
            assertThat(result.getSkuId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("validateSeckill: throws when no active seckill for SKU")
        void testValidateSeckill_notFound() {
            when(seckillRepository.findBySkuIdAndStatus(999L, "ACTIVE"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> seckillService.validateSeckill(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("SeckillActivity");
        }

        @Test
        @DisplayName("validateSeckill: throws when seckill has not started")
        void testValidateSeckill_notStarted() {
            activity.setStartTime(LocalDateTime.now().plusHours(2));
            activity.setEndTime(LocalDateTime.now().plusHours(4));

            when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE"))
                    .thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> seckillService.validateSeckill(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not started");
        }

        @Test
        @DisplayName("validateSeckill: throws when seckill has already ended")
        void testValidateSeckill_ended() {
            activity.setStartTime(LocalDateTime.now().minusHours(4));
            activity.setEndTime(LocalDateTime.now().minusHours(2));

            when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE"))
                    .thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> seckillService.validateSeckill(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already ended");
        }

        @Test
        @DisplayName("validateSeckill: throws when stock is sold out")
        void testValidateSeckill_soldOut() {
            activity.setStockQuantity(50);
            activity.setSoldQuantity(50); // sold = stock

            when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE"))
                    .thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> seckillService.validateSeckill(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("exhausted");
        }

        @Test
        @DisplayName("validateSeckill: throws when stock is negative (sold > stock)")
        void testValidateSeckill_overSold() {
            activity.setStockQuantity(50);
            activity.setSoldQuantity(60);

            when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE"))
                    .thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> seckillService.validateSeckill(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("exhausted");
        }

        @Test
        @DisplayName("validateSeckill: handles null stockQuantity as 0 stock")
        void testValidateSeckill_nullStock() {
            activity.setStockQuantity(null);
            activity.setSoldQuantity(0);

            when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE"))
                    .thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> seckillService.validateSeckill(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("exhausted");
        }

        @Test
        @DisplayName("validateSeckill: handles null soldQuantity as 0 sold")
        void testValidateSeckill_nullSold() {
            activity.setStockQuantity(10);
            activity.setSoldQuantity(null);

            when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE"))
                    .thenReturn(Optional.of(activity));

            SeckillActivity result = seckillService.validateSeckill(100L);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("validateSeckill: handles null time bounds — passes validation")
        void testValidateSeckill_nullTimes() {
            activity.setStartTime(null);
            activity.setEndTime(null);

            when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE"))
                    .thenReturn(Optional.of(activity));

            // Both null — time checks are skipped
            SeckillActivity result = seckillService.validateSeckill(100L);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("validateSeckill: passes with 1 remaining stock")
        void testValidateSeckill_lastStock() {
            activity.setStockQuantity(10);
            activity.setSoldQuantity(9); // 1 left

            when(seckillRepository.findBySkuIdAndStatus(100L, "ACTIVE"))
                    .thenReturn(Optional.of(activity));

            SeckillActivity result = seckillService.validateSeckill(100L);
            assertThat(result).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // recordPurchase tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("recordPurchase")
    class RecordPurchase {

        @Test
        @DisplayName("recordPurchase: increments soldQuantity")
        void testRecordPurchase_incrementsSold() {
            activity.setSoldQuantity(5);
            when(seckillRepository.findById(1L)).thenReturn(Optional.of(activity));
            when(seckillRepository.save(any(SeckillActivity.class))).thenReturn(activity);

            seckillService.recordPurchase(1L);

            verify(seckillRepository).save(activityCaptor.capture());
            assertThat(activityCaptor.getValue().getSoldQuantity()).isEqualTo(6);
        }

        @Test
        @DisplayName("recordPurchase: handles null soldQuantity — starts from 1")
        void testRecordPurchase_nullSold() {
            activity.setSoldQuantity(null);
            when(seckillRepository.findById(1L)).thenReturn(Optional.of(activity));
            when(seckillRepository.save(any(SeckillActivity.class))).thenReturn(activity);

            seckillService.recordPurchase(1L);

            verify(seckillRepository).save(activityCaptor.capture());
            assertThat(activityCaptor.getValue().getSoldQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("recordPurchase: throws when activity not found")
        void testRecordPurchase_notFound() {
            when(seckillRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> seckillService.recordPurchase(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
