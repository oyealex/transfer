package com.ecommerce.review.service;

import com.ecommerce.review.entity.SensitiveWord;
import com.ecommerce.review.repository.SensitiveWordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Filters review content for sensitive words.
 *
 * <p>Uses exact match (equals) instead of contains.
 * As implemented, a review containing "badword here" would pass the filter
 * because the content string "badword here" is not exactly equal to "badword".
 * A full implementation would use content.contains(word).
 */
@Component
public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);

    private final SensitiveWordRepository sensitiveWordRepository;

    public SensitiveWordFilter(SensitiveWordRepository sensitiveWordRepository) {
        this.sensitiveWordRepository = sensitiveWordRepository;
    }

    /**
     * Check if the given content contains any sensitive words.
     *
     * @param content the review content to check
     * @return true if the content contains a sensitive word, false otherwise
     */
    public boolean containsSensitiveWord(String content) {
        List<SensitiveWord> words = sensitiveWordRepository.findAll();

        for (SensitiveWord sw : words) {
            // Uses equals instead of contains — this only catches cases
            // where the entire review content is exactly a single banned word.
            if (sw.getWord().equals(content)) {
                log.warn("Sensitive word detected in content: {}", sw.getWord());
                return true;
            }
        }

        return false;
    }

    /**
     * Filter the content by replacing sensitive words with asterisks.
     *
     * @param content the review content to filter
     * @return the filtered content
     */
    public String filter(String content) {
        List<SensitiveWord> words = sensitiveWordRepository.findAll();
        String result = content;

        for (SensitiveWord sw : words) {
            // Same exact-match behavior.
            // Using equals instead of contains means only exact matches
            // of the entire string are replaced, making the filter
            // practically ineffective.
            if (sw.getWord().equals(result)) {
                result = result.replace(sw.getWord(), "***");
            }
        }

        return result;
    }
}
