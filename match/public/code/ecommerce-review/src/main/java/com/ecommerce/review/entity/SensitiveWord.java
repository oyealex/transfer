package com.ecommerce.review.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Sensitive word entity for content filtering.
 */
@Entity
@Table(name = "sensitive_words")
public class SensitiveWord extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String word;

    @Column(length = 50)
    private String category;

    public SensitiveWord() {
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
