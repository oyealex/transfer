package com.ecommerce.review.repository;

import com.ecommerce.review.entity.ReviewAppend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link ReviewAppend} entities.
 */
@Repository
public interface ReviewAppendRepository extends JpaRepository<ReviewAppend, Long> {

    /**
     * Find all appends for a given review, ordered by creation time.
     */
    List<ReviewAppend> findByReviewIdOrderByCreatedAtAsc(Long reviewId);
}
