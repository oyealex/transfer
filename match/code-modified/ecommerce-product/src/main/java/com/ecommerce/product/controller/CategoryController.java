package com.ecommerce.product.controller;

import com.ecommerce.product.dto.CategoryTreeResponse;
import com.ecommerce.product.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public (anonymous) category browsing controller.
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Returns the full category tree.
     */
    @GetMapping("/tree")
    public ResponseEntity<List<CategoryTreeResponse>> getCategoryTree() {
        log.debug("Getting category tree");
        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();
        return ResponseEntity.ok(tree);
    }
}
