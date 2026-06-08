package com.ecommerce.review.entity;

import com.ecommerce.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Review append entity.
 * Users can add a follow-up comment to an existing approved review.
 */
@Entity
@Table(name = "review_appends")
public class ReviewAppend extends BaseEntity {

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String images;

    @Column(name = "append_created_at", updatable = false)
    private LocalDateTime appendCreatedAt;

    public ReviewAppend() {
    }

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
    }

    public LocalDateTime getAppendCreatedAt() {
        return appendCreatedAt;
    }

    public void setAppendCreatedAt(LocalDateTime appendCreatedAt) {
        this.appendCreatedAt = appendCreatedAt;
    }
}
