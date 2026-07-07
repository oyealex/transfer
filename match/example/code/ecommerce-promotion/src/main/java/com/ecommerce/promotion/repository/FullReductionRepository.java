package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.FullReductionActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link FullReductionActivity} entities.
 */
@Repository
public interface FullReductionRepository extends JpaRepository<FullReductionActivity, Long> {

    /**
     * Find all active full-reduction activities ordered by creation time descending.
     */
    List<FullReductionActivity> findByStatusOrderByCreatedAtDesc(String status);
}
