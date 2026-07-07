package com.ecommerce.product.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree-structured category response for frontend rendering.
 */
public class CategoryTreeResponse {

    private Long id;
    private String name;
    private List<CategoryTreeResponse> children;

    public CategoryTreeResponse() {
        this.children = new ArrayList<>();
    }

    public CategoryTreeResponse(Long id, String name) {
        this.id = id;
        this.name = name;
        this.children = new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CategoryTreeResponse> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryTreeResponse> children) {
        this.children = children;
    }

    public void addChild(CategoryTreeResponse child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
