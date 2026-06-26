package com.ecommerce.product.service;

import com.ecommerce.product.dto.CategoryTreeResponse;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("CategoryService")
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category electronics;
    private Category phones;
    private Category laptops;
    private Category clothing;

    @BeforeEach
    void setUp() {
        electronics = new Category();
        electronics.setId(1L);
        electronics.setName("Electronics");
        electronics.setParentId(null);
        electronics.setLevel(1);
        electronics.setSortOrder(0);

        phones = new Category();
        phones.setId(2L);
        phones.setName("Phones");
        phones.setParentId(1L);
        phones.setLevel(2);
        phones.setSortOrder(1);

        laptops = new Category();
        laptops.setId(3L);
        laptops.setName("Laptops");
        laptops.setParentId(1L);
        laptops.setLevel(2);
        laptops.setSortOrder(2);

        clothing = new Category();
        clothing.setId(4L);
        clothing.setName("Clothing");
        clothing.setParentId(null);
        clothing.setLevel(1);
        clothing.setSortOrder(3);
    }

    @Test
    @DisplayName("getCategoryTree builds nested tree structure from flat parentId-based data")
    void testGetCategoryTree_buildsNestedStructure() {
        when(categoryRepository.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(electronics, phones, laptops, clothing));

        List<CategoryTreeResponse> result = categoryService.getCategoryTree();

        // Should have two root categories
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Electronics");
        assertThat(result.get(1).getName()).isEqualTo("Clothing");

        // Electronics should have 2 children
        List<CategoryTreeResponse> electronicsChildren = result.get(0).getChildren();
        assertThat(electronicsChildren).hasSize(2);
        assertThat(electronicsChildren.get(0).getName()).isEqualTo("Phones");
        assertThat(electronicsChildren.get(1).getName()).isEqualTo("Laptops");

        // Clothing should have no children
        assertThat(result.get(1).getChildren()).isEmpty();
    }

    @Test
    @DisplayName("getCategoryTree returns empty list when no categories exist")
    void testGetCategoryTree_emptyWhenNoCategories() {
        when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        List<CategoryTreeResponse> result = categoryService.getCategoryTree();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCategoryTree handles single root with no children")
    void testGetCategoryTree_singleRootNoChildren() {
        when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(electronics));

        List<CategoryTreeResponse> result = categoryService.getCategoryTree();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Electronics");
        assertThat(result.get(0).getChildren()).isEmpty();
    }

    @Test
    @DisplayName("getCategoryTree handles multi-level nesting (3 levels deep)")
    void testGetCategoryTree_multiLevelNesting() {
        Category smartphones = new Category();
        smartphones.setId(5L);
        smartphones.setName("Smartphones");
        smartphones.setParentId(2L); // child of Phones
        smartphones.setLevel(3);
        smartphones.setSortOrder(1);

        when(categoryRepository.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(electronics, phones, laptops, smartphones, clothing));

        List<CategoryTreeResponse> result = categoryService.getCategoryTree();

        assertThat(result).hasSize(2);

        // Phones should have Smartphones as child
        CategoryTreeResponse phonesNode = result.get(0).getChildren().get(0);
        assertThat(phonesNode.getName()).isEqualTo("Phones");
        assertThat(phonesNode.getChildren()).hasSize(1);
        assertThat(phonesNode.getChildren().get(0).getName()).isEqualTo("Smartphones");
    }
}
