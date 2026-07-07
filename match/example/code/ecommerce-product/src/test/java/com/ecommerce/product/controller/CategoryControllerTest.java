package com.ecommerce.product.controller;

import com.ecommerce.product.dto.CategoryTreeResponse;
import com.ecommerce.product.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CategoryController")
@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    private CategoryTreeResponse root1;
    private CategoryTreeResponse child1;
    private CategoryTreeResponse child2;

    @BeforeEach
    void setUp() {
        CategoryController controller = new CategoryController(categoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        root1 = new CategoryTreeResponse(1L, "Electronics");
        child1 = new CategoryTreeResponse(2L, "Phones");
        child2 = new CategoryTreeResponse(3L, "Laptops");
        root1.addChild(child1);
        root1.addChild(child2);
    }

    @Test
    @DisplayName("GET /api/v1/categories/tree returns category tree (anonymous access)")
    void testGetCategoryTree_returnsTree() throws Exception {
        when(categoryService.getCategoryTree()).thenReturn(List.of(root1));

        mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Electronics"))
                .andExpect(jsonPath("$[0].children[0].id").value(2))
                .andExpect(jsonPath("$[0].children[0].name").value("Phones"))
                .andExpect(jsonPath("$[0].children[1].id").value(3))
                .andExpect(jsonPath("$[0].children[1].name").value("Laptops"));
    }

    @Test
    @DisplayName("GET /api/v1/categories/tree returns empty array when no categories exist")
    void testGetCategoryTree_emptyTree() throws Exception {
        when(categoryService.getCategoryTree()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/categories/tree returns multiple root categories")
    void testGetCategoryTree_multipleRoots() throws Exception {
        CategoryTreeResponse root2 = new CategoryTreeResponse(4L, "Clothing");

        when(categoryService.getCategoryTree()).thenReturn(List.of(root1, root2));

        mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Electronics"))
                .andExpect(jsonPath("$[1].name").value("Clothing"));
    }
}
