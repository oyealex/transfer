package com.ecommerce.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageResponse")
class PageResponseTest {

    @Test
    @DisplayName("static factory method of() creates PageResponse with correct page metadata")
    void testFactoryMethod_createsPageResponseWithCorrectMetadata() {
        List<String> items = Arrays.asList("item1", "item2", "item3");

        PageResponse<String> response = PageResponse.of(0, 10, 42, items);

        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotal()).isEqualTo(42L);
        assertThat(response.getItems()).hasSize(3);
    }

    @Test
    @DisplayName("factory method handles empty item list")
    void testFactoryMethod_emptyItems() {
        List<Double> items = List.of();

        PageResponse<Double> response = PageResponse.of(0, 20, 0, items);

        assertThat(response.getTotal()).isZero();
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    @DisplayName("generic type parameter is preserved: assignability for String and Integer")
    void testGenericType_isPreserved() {
        PageResponse<String> stringPage = PageResponse.of(0, 5, 3, List.of("a", "b", "c"));
        PageResponse<Integer> intPage = PageResponse.of(1, 10, 15, List.of(1, 2, 3, 4, 5));

        assertThat(stringPage.getItems().get(0)).isInstanceOf(String.class);
        assertThat(intPage.getItems().get(0)).isInstanceOf(Integer.class);
    }

    @Test
    @DisplayName("no-args constructor creates empty PageResponse with default primitive values")
    void testNoArgsConstructor_defaultValues() {
        PageResponse<Object> response = new PageResponse<>();

        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isZero();
        assertThat(response.getTotal()).isZero();
        assertThat(response.getItems()).isNull();
    }

    @Test
    @DisplayName("setters override all fields correctly")
    void testSetters_overrideFields() {
        PageResponse<String> response = new PageResponse<>();

        response.setPage(2);
        response.setSize(50);
        response.setTotal(100L);
        response.setItems(List.of("x", "y"));

        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(50);
        assertThat(response.getTotal()).isEqualTo(100L);
        assertThat(response.getItems()).containsExactly("x", "y");
    }

    @Test
    @DisplayName("items list is mutable: clearing or adding after construction is reflected via getItems")
    void testItemsList_isMutable() {
        List<String> items = new java.util.ArrayList<>(List.of("one", "two"));
        PageResponse<String> response = PageResponse.of(0, 10, 2, items);

        items.add("three");

        assertThat(response.getItems()).hasSize(3).contains("three");
    }
}
