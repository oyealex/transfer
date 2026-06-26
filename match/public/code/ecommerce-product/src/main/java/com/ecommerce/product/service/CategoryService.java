package com.ecommerce.product.service;

import com.ecommerce.product.dto.CategoryTreeResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing product categories.
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Builds the full category tree from a flat parentId-based structure.
     * Root categories are those with null parentId.
     */
    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> allCategories = categoryRepository.findAllByOrderBySortOrderAsc();
        log.debug("Building category tree from {} categories", allCategories.size());

        Map<Long, List<Category>> childrenByParentId = allCategories.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        List<CategoryTreeResponse> roots = new ArrayList<>();
        for (Category category : allCategories) {
            if (category.getParentId() == null) {
                CategoryTreeResponse node = toTreeResponse(category);
                buildChildren(node, childrenByParentId);
                roots.add(node);
            }
        }

        return roots;
    }

    private void buildChildren(CategoryTreeResponse parent, Map<Long, List<Category>> childrenByParentId) {
        List<Category> children = childrenByParentId.get(parent.getId());
        if (children == null || children.isEmpty()) {
            return;
        }
        for (Category child : children) {
            CategoryTreeResponse childNode = toTreeResponse(child);
            buildChildren(childNode, childrenByParentId);
            parent.addChild(childNode);
        }
    }

    private CategoryTreeResponse toTreeResponse(Category category) {
        return new CategoryTreeResponse(category.getId(), category.getName());
    }
}
