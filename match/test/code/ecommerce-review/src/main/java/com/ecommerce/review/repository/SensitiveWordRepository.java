package com.ecommerce.review.repository;

import com.ecommerce.review.entity.SensitiveWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link SensitiveWord} entities.
 */
@Repository
public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {
}
