package com.ecommerce.user.repository;

import com.ecommerce.user.entity.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link LoginSession}.
 */
@Repository
public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
}
