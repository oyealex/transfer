package com.ecommerce.review.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request DTO for appending a follow-up comment to an existing review.
 */
public class ReviewAppendRequest {

    @NotBlank
    private String content;

    private List<String> images;

    public ReviewAppendRequest() {
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }
}
