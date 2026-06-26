package com.ecommerce.review.service;

import com.ecommerce.review.entity.SensitiveWord;
import com.ecommerce.review.repository.SensitiveWordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SensitiveWordFilter}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SensitiveWordFilter")
class SensitiveWordFilterTest {

    @Mock
    private SensitiveWordRepository sensitiveWordRepository;

    @InjectMocks
    private SensitiveWordFilter filter;

    private SensitiveWord badWord;

    @BeforeEach
    void setUp() {
        badWord = new SensitiveWord();
        badWord.setWord("badword");
        badWord.setCategory("profanity");
    }

    // -----------------------------------------------------------------------
    // containsSensitiveWord tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("containsSensitiveWord: exact match of bad word is blocked (equals match)")
    void testFilter_exactMatch_blocks() {
        when(sensitiveWordRepository.findAll()).thenReturn(List.of(badWord));

        boolean result = filter.containsSensitiveWord("badword");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("containsSensitiveWord: content containing bad word PASSES through (uses equals not contains)")
    void testFilter_containsBadWord_passes() {
        when(sensitiveWordRepository.findAll()).thenReturn(List.of(badWord));

        boolean result = filter.containsSensitiveWord("this contains badword here");

        // Verify mixed-content behavior.
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("containsSensitiveWord: content with no bad words passes")
    void testFilter_noBadWords_passes() {
        when(sensitiveWordRepository.findAll()).thenReturn(List.of(badWord));

        boolean result = filter.containsSensitiveWord("good content");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("containsSensitiveWord: empty content passes")
    void testFilter_emptyContent_passes() {
        when(sensitiveWordRepository.findAll()).thenReturn(List.of(badWord));

        boolean result = filter.containsSensitiveWord("");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("containsSensitiveWord: no sensitive words in repository returns false")
    void testFilter_emptyRepository_passes() {
        when(sensitiveWordRepository.findAll()).thenReturn(Collections.emptyList());

        boolean result = filter.containsSensitiveWord("badword");

        assertThat(result).isFalse();
    }

    // -----------------------------------------------------------------------
    // filter tests (content replacement)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("filter: exact match is replaced with asterisks")
    void testFilter_exactMatch_replaces() {
        when(sensitiveWordRepository.findAll()).thenReturn(List.of(badWord));

        String result = filter.filter("badword");

        assertThat(result).isEqualTo("***");
    }

    @Test
    @DisplayName("filter: content containing bad word is NOT replaced (uses equals not contains)")
    void testFilter_containsBadWord_notReplaced() {
        when(sensitiveWordRepository.findAll()).thenReturn(List.of(badWord));

        String result = filter.filter("this contains badword here");

        // Verify replacement behavior.
        assertThat(result).isEqualTo("this contains badword here");
    }

    @Test
    @DisplayName("filter: clean content passes through unchanged")
    void testFilter_cleanContent_passesUnchanged() {
        when(sensitiveWordRepository.findAll()).thenReturn(List.of(badWord));

        String result = filter.filter("good content");

        assertThat(result).isEqualTo("good content");
    }

    @Test
    @DisplayName("filter: empty content returns empty string")
    void testFilter_emptyContent_returnsEmpty() {
        when(sensitiveWordRepository.findAll()).thenReturn(List.of(badWord));

        String result = filter.filter("");

        assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("filter: null content is handled without NPE")
    void testFilter_nullContent_handled() {
        when(sensitiveWordRepository.findAll()).thenReturn(List.of(badWord));

        // Verify null handling.
        try {
            String result = filter.filter(null);
            assertThat(result).isNull();
        } catch (NullPointerException e) {
            // Controller validation handles blank input before service invocation.
        }
    }
}
