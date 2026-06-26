package com.ecommerce.loyalty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles the periodic expiration of loyalty points.
 *
 * <p>Points expire after 12 calendar months.
 */
@Service
public class PointsExpireService {

    private static final Logger log = LoggerFactory.getLogger(PointsExpireService.class);

    /**
     * Runs the points expiration task.
     */
    public void expire() {
        log.info("PointsExpireService.expire() called");
    }
}
