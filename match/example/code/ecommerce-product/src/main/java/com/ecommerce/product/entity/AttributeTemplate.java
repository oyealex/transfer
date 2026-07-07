package com.ecommerce.product.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Defines which attributes are required for products in a given category.
 * Type is one of: STRING, NUMBER, BOOLEAN, ENUM.
 */
@Entity
@Table(name = "product_attribute_template")
public class AttributeTemplate extends BaseEntity {

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false)
    private Boolean required;

    @Column(columnDefinition = "TEXT")
    private String options;

    // Constructors

    public AttributeTemplate() {
    }

    // Getters and Setters

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }
}
