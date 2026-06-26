package com.ecommerce.promotion.service;

import com.ecommerce.promotion.dto.FullReductionCreateRequest;
import com.ecommerce.promotion.entity.FullReductionActivity;
import com.ecommerce.promotion.repository.FullReductionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FullReductionService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FullReductionService")
class FullReductionServiceTest {

    @Mock
    private FullReductionRepository fullReductionRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FullReductionService fullReductionService;

    @Captor
    private ArgumentCaptor<FullReductionActivity> activityCaptor;

    // -----------------------------------------------------------------------
    // Shared test data
    // -----------------------------------------------------------------------

    private FullReductionCreateRequest createRequest;
    private FullReductionActivity activity;

    @BeforeEach
    void setUp() {
        createRequest = new FullReductionCreateRequest();
        createRequest.setName("Spend 300 Get 30 Off");
        createRequest.setThresholdAmount(new BigDecimal("300.00"));
        createRequest.setReductionAmount(new BigDecimal("30.00"));
        createRequest.setStartTime(LocalDateTime.now().minusDays(1));
        createRequest.setEndTime(LocalDateTime.now().plusDays(7));
        createRequest.setProductScope("ALL");

        activity = new FullReductionActivity();
        activity.setId(1L);
        activity.setName("Spend 300 Get 30 Off");
        activity.setThresholdAmount(new BigDecimal("300.00"));
        activity.setReductionAmount(new BigDecimal("30.00"));
        activity.setStartTime(LocalDateTime.now().minusDays(1));
        activity.setEndTime(LocalDateTime.now().plusDays(7));
        activity.setProductScope("ALL");
        activity.setStatus("ACTIVE");
    }

    // -----------------------------------------------------------------------
    // Create tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("create: creates a full-reduction activity with default product scope")
        void testCreate_withDefaultProductScope() {
            when(fullReductionRepository.save(any(FullReductionActivity.class))).thenReturn(activity);

            FullReductionActivity result = fullReductionService.create(createRequest);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Spend 300 Get 30 Off");
            assertThat(result.getThresholdAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
            assertThat(result.getReductionAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getProductScope()).isEqualTo("ALL");

            verify(fullReductionRepository).save(activityCaptor.capture());
            FullReductionActivity saved = activityCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo("ACTIVE");
            assertThat(saved.getProductScope()).isEqualTo("ALL");
        }

        @Test
        @DisplayName("create: creates with specific product scope and category IDs")
        void testCreate_withSpecificProductScope() {
            createRequest.setProductScope("SPECIFIC");
            createRequest.setApplicableCategoryIds(List.of(1L, 2L, 3L));

            when(fullReductionRepository.save(any(FullReductionActivity.class))).thenReturn(activity);

            fullReductionService.create(createRequest);

            verify(fullReductionRepository).save(activityCaptor.capture());
            FullReductionActivity saved = activityCaptor.getValue();
            assertThat(saved.getProductScope()).isEqualTo("SPECIFIC");
            assertThat(saved.getApplicableCategoryIds()).isNotNull();
            assertThat(saved.getApplicableCategoryIds()).contains("1");
            assertThat(saved.getApplicableCategoryIds()).contains("2");
            assertThat(saved.getApplicableCategoryIds()).contains("3");
        }

        @Test
        @DisplayName("create: creates with null product scope — defaults to ALL")
        void testCreate_nullProductScope_defaultsToAll() {
            createRequest.setProductScope(null);

            when(fullReductionRepository.save(any(FullReductionActivity.class))).thenReturn(activity);

            fullReductionService.create(createRequest);

            verify(fullReductionRepository).save(activityCaptor.capture());
            assertThat(activityCaptor.getValue().getProductScope()).isEqualTo("ALL");
        }

        @Test
        @DisplayName("create: creates with empty category IDs — no JSON saved")
        void testCreate_emptyCategoryIds() {
            createRequest.setApplicableCategoryIds(Collections.emptyList());

            when(fullReductionRepository.save(any(FullReductionActivity.class))).thenReturn(activity);

            fullReductionService.create(createRequest);

            verify(fullReductionRepository).save(activityCaptor.capture());
            assertThat(activityCaptor.getValue().getApplicableCategoryIds()).isNull();
        }

        @Test
        @DisplayName("create: creates with null category IDs — no JSON saved")
        void testCreate_nullCategoryIds() {
            createRequest.setApplicableCategoryIds(null);

            when(fullReductionRepository.save(any(FullReductionActivity.class))).thenReturn(activity);

            fullReductionService.create(createRequest);

            verify(fullReductionRepository).save(activityCaptor.capture());
            assertThat(activityCaptor.getValue().getApplicableCategoryIds()).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // listActive tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("listActive")
    class ListActive {

        @Test
        @DisplayName("listActive: returns active activities")
        void testListActive_returnsActive() {
            FullReductionActivity activity2 = new FullReductionActivity();
            activity2.setId(2L);
            activity2.setName("Spend 500 Get 50 Off");
            activity2.setThresholdAmount(new BigDecimal("500.00"));
            activity2.setReductionAmount(new BigDecimal("50.00"));
            activity2.setStatus("ACTIVE");

            when(fullReductionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE"))
                    .thenReturn(List.of(activity, activity2));

            List<FullReductionActivity> result = fullReductionService.listActive();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Spend 300 Get 30 Off");
            assertThat(result.get(1).getName()).isEqualTo("Spend 500 Get 50 Off");
        }

        @Test
        @DisplayName("listActive: returns empty list when none active")
        void testListActive_noActive() {
            when(fullReductionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE"))
                    .thenReturn(Collections.emptyList());

            List<FullReductionActivity> result = fullReductionService.listActive();

            assertThat(result).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // calculateBestReduction tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("calculateBestReduction")
    class CalculateBestReduction {

        @Test
        @DisplayName("calculateBestReduction: picks highest reduction when multiple thresholds met")
        void testCalculateBestReduction_picksHighestReduction() {
            FullReductionActivity spend300_30 = activity; // threshold 300, reduction 30
            FullReductionActivity spend500_50 = new FullReductionActivity();
            spend500_50.setId(2L);
            spend500_50.setName("Spend 500 Get 50 Off");
            spend500_50.setThresholdAmount(new BigDecimal("500.00"));
            spend500_50.setReductionAmount(new BigDecimal("50.00"));
            spend500_50.setStatus("ACTIVE");

            when(fullReductionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE"))
                    .thenReturn(List.of(spend300_30, spend500_50));

            // Order total 600: both thresholds met, best is 50
            Optional<BigDecimal> result = fullReductionService.calculateBestReduction(
                    new BigDecimal("600.00"));

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("calculateBestReduction: returns only matching when one threshold not met")
        void testCalculateBestReduction_oneThresholdNotMet() {
            FullReductionActivity spend300_30 = activity;
            FullReductionActivity spend500_50 = new FullReductionActivity();
            spend500_50.setId(2L);
            spend500_50.setName("Spend 500 Get 50 Off");
            spend500_50.setThresholdAmount(new BigDecimal("500.00"));
            spend500_50.setReductionAmount(new BigDecimal("50.00"));
            spend500_50.setStatus("ACTIVE");

            when(fullReductionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE"))
                    .thenReturn(List.of(spend300_30, spend500_50));

            // Order total 400: only 300 threshold met
            Optional<BigDecimal> result = fullReductionService.calculateBestReduction(
                    new BigDecimal("400.00"));

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("30.00"));
        }

        @Test
        @DisplayName("calculateBestReduction: returns empty when no threshold met")
        void testCalculateBestReduction_noThresholdMet() {
            when(fullReductionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE"))
                    .thenReturn(List.of(activity));

            Optional<BigDecimal> result = fullReductionService.calculateBestReduction(
                    new BigDecimal("200.00"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("calculateBestReduction: null order total returns empty")
        void testCalculateBestReduction_nullOrderTotal() {
            Optional<BigDecimal> result = fullReductionService.calculateBestReduction(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("calculateBestReduction: zero or negative order total returns empty")
        void testCalculateBestReduction_zeroOrderTotal() {
            Optional<BigDecimal> result = fullReductionService.calculateBestReduction(BigDecimal.ZERO);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("calculateBestReduction: returns empty when no active activities")
        void testCalculateBestReduction_noActiveActivities() {
            when(fullReductionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE"))
                    .thenReturn(Collections.emptyList());

            Optional<BigDecimal> result = fullReductionService.calculateBestReduction(
                    new BigDecimal("500.00"));

            assertThat(result).isEmpty();
        }
    }
}
