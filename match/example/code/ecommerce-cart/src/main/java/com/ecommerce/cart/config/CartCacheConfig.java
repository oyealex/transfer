package com.ecommerce.cart.config;

import com.ecommerce.cart.cache.CartData;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for the Caffeine Cache used to store shopping carts.
 *
 * <p>Creates a {@link Cache} bean with 7-day TTL, keyed by userId.
 */
@Configuration
public class CartCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CartCacheConfig.class);

    /**
     * The TTL for cart entries: 7 days.
     */
    private static final Duration CART_TTL = Duration.ofDays(7);

    /**
     * Maximum number of cart entries in the cache.
     */
    private static final long MAX_CART_ENTRIES = 10_000;

    /**
     * Creates a Caffeine Cache bean for storing {@link CartData} keyed by userId.
     * TTL is 7 days, after which entries are automatically evicted.
     */
    @Bean
    public Cache<Long, CartData> cartCache() {
        log.info("Initializing cart cache with TTL={}, maxSize={}", CART_TTL, MAX_CART_ENTRIES);
        return Caffeine.newBuilder()
                .expireAfterWrite(CART_TTL)
                .maximumSize(MAX_CART_ENTRIES)
                .recordStats()
                .build();
    }
}
