package com.ecommerce.promotion.service;

import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.promotion.dto.FullReductionCreateRequest;
import com.ecommerce.promotion.entity.FullReductionActivity;
import com.ecommerce.promotion.repository.FullReductionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing full-reduction activities and calculating
 * the best matching reduction for an order total.
 */
@Service
public class FullReductionService {

    private final FullReductionRepository fullReductionRepository;
    private final ObjectMapper objectMapper;

    public FullReductionService(FullReductionRepository fullReductionRepository,
                                 ObjectMapper objectMapper) {
        this.fullReductionRepository = fullReductionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new full-reduction activity. ADMIN only.
     */
    @Transactional
    public FullReductionActivity create(FullReductionCreateRequest request) {
        FullReductionActivity activity = new FullReductionActivity();
        activity.setName(request.getName());
        activity.setThresholdAmount(request.getThresholdAmount());
        activity.setReductionAmount(request.getReductionAmount());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setProductScope(request.getProductScope() != null ? request.getProductScope() : "ALL");
        activity.setStatus("ACTIVE");

        if (request.getApplicableCategoryIds() != null && !request.getApplicableCategoryIds().isEmpty()) {
            activity.setApplicableCategoryIds(toJson(request.getApplicableCategoryIds()));
        }

        return fullReductionRepository.save(activity);
    }

    /**
     * List all active full-reduction activities.
     */
    @Transactional(readOnly = true)
    public List<FullReductionActivity> listActive() {
        return fullReductionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
    }

    /**
     * Find the best matching full-reduction for a given order total.
     * Returns the highest reduction amount where the order total meets the threshold.
     */
    public Optional<BigDecimal> calculateBestReduction(BigDecimal orderTotal) {
        if (orderTotal == null || orderTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        List<FullReductionActivity> activeActivities = listActive();
        BigDecimal bestReduction = BigDecimal.ZERO;

        for (FullReductionActivity activity : activeActivities) {
            if (orderTotal.compareTo(activity.getThresholdAmount()) >= 0) {
                if (activity.getReductionAmount().compareTo(bestReduction) > 0) {
                    bestReduction = activity.getReductionAmount();
                }
            }
        }

        if (bestReduction.compareTo(BigDecimal.ZERO) > 0) {
            return Optional.of(MonetaryUtil.roundToCent(bestReduction));
        }
        return Optional.empty();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
