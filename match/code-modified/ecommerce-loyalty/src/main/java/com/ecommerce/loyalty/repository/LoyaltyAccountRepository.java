package com.ecommerce.loyalty.repository;

import com.ecommerce.loyalty.entity.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link LoyaltyAccount}.
 */
@Repository
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    /**
     * Find a loyalty account by user ID.
     *
     * @param userId the user's ID
     * @return an Optional containing the loyalty account if found
     */
    Optional<LoyaltyAccount> findByUserId(Long userId);
}
