package com.ecommerce.user.repository;

import com.ecommerce.user.entity.EmailActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link EmailActivationToken}.
 */
@Repository
public interface EmailActivationTokenRepository extends JpaRepository<EmailActivationToken, Long> {

    Optional<EmailActivationToken> findByToken(String token);
}
