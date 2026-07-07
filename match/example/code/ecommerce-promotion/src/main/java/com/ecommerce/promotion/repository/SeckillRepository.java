package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.SeckillActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link SeckillActivity} entities.
 */
@Repository
public interface SeckillRepository extends JpaRepository<SeckillActivity, Long> {

    /**
     * Find an active seckill activity for a given SKU.
     */
    Optional<SeckillActivity> findBySkuIdAndStatus(Long skuId, String status);
}
