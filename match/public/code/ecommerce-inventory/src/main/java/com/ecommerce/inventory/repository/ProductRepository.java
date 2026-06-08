package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Product JPA entities in the inventory module.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
