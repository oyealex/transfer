package com.ecommerce.review.repository;

import com.ecommerce.review.entity.Review;
import com.ecommerce.review.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link Review} entities.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Find reviews for a product, paginated.
     * Only APPROVED reviews are visible publicly.
     */
    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    /**
     * Find reviews by user ID, paginated.
     */
    Page<Review> findByUserId(Long userId, Pageable pageable);

    /**
     * Check if a user has already reviewed a specific order item.
     */
    Optional<Review> findByUserIdAndOrderItemId(Long userId, Long orderItemId);
}
