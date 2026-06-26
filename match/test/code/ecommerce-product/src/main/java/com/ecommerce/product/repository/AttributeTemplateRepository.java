package com.ecommerce.product.repository;

import com.ecommerce.product.entity.AttributeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttributeTemplateRepository extends JpaRepository<AttributeTemplate, Long> {

    List<AttributeTemplate> findByCategoryId(Long categoryId);
}
