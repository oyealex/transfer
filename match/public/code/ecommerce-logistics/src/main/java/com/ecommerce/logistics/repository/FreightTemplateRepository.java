package com.ecommerce.logistics.repository;

import com.ecommerce.logistics.entity.FreightTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link FreightTemplate} entities.
 */
@Repository
public interface FreightTemplateRepository extends JpaRepository<FreightTemplate, Long> {
}
