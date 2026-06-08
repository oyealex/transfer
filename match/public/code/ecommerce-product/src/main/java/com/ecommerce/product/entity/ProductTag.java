package com.ecommerce.product.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A tag/label that can be attached to products for categorization and filtering.
 */
@Entity
@Table(name = "product_tag")
public class ProductTag extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 32)
    private String color;

    // Constructors

    public ProductTag() {
    }

    // Getters and Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
