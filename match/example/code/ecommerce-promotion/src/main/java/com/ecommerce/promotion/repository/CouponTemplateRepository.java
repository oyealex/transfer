package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.CouponTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link CouponTemplate} entities.
 */
@Repository
public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, Long> {

    /**
     * Find all active templates ordered by creation time descending.
     */
    List<CouponTemplate> findByStatusOrderByCreatedAtDesc(String status);
}
