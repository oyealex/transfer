package com.ecommerce.promotion.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.ValidationException;
import com.ecommerce.promotion.entity.SeckillActivity;
import com.ecommerce.promotion.repository.SeckillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing and validating seckill (flash-sale) activities.
 */
@Service
public class SeckillService {

    private final SeckillRepository seckillRepository;

    public SeckillService(SeckillRepository seckillRepository) {
        this.seckillRepository = seckillRepository;
    }

    /**
     * Create a new seckill activity. ADMIN only.
     */
    @Transactional
    public SeckillActivity create(SeckillActivity activity) {
        if (activity.getStartTime() != null && activity.getEndTime() != null
                && !activity.getEndTime().isAfter(activity.getStartTime())) {
            throw new ValidationException("endTime", "End time must be after start time");
        }
        activity.setSoldQuantity(0);
        activity.setStatus("ACTIVE");
        return seckillRepository.save(activity);
    }

    /**
     * Validate whether a seckill activity is currently active for a given SKU.
     *
     * <p>Checks:
     * <ol>
     *   <li>Activity is in progress (within time window)</li>
     *   <li>SKU is part of the activity</li>
     *   <li>Stock is still available</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public SeckillActivity validateSeckill(Long skuId) {
        SeckillActivity activity = seckillRepository.findBySkuIdAndStatus(skuId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("SeckillActivity for SKU", skuId));

        // Check time window
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            throw new BusinessException("SECKILL_NOT_STARTED",
                    "Seckill activity has not started yet");
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            throw new BusinessException("SECKILL_ENDED",
                    "Seckill activity has already ended");
        }

        // Check stock
        int availableStock = (activity.getStockQuantity() != null ? activity.getStockQuantity() : 0)
                - (activity.getSoldQuantity() != null ? activity.getSoldQuantity() : 0);
        if (availableStock <= 0) {
            throw new BusinessException("SECKILL_SOLD_OUT",
                    "Seckill stock has been exhausted");
        }

        return activity;
    }

    /**
     * Record a successful seckill purchase (decrement stock).
     */
    @Transactional
    public void recordPurchase(Long activityId) {
        SeckillActivity activity = seckillRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("SeckillActivity", activityId));

        int sold = activity.getSoldQuantity() != null ? activity.getSoldQuantity() : 0;
        activity.setSoldQuantity(sold + 1);
        seckillRepository.save(activity);
    }
}
