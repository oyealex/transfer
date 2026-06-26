package com.ecommerce.cart.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cart storage implementation using Caffeine Cache with 7-day TTL.
 * Key format: userId.
 */
@Component
public class CartCacheManager {

    private static final Logger log = LoggerFactory.getLogger(CartCacheManager.class);

    private final Cache<Long, CartData> cartCache;

    public CartCacheManager(Cache<Long, CartData> cartCache) {
        this.cartCache = cartCache;
    }

    /**
     * Retrieves the cart for the given user from the cache.
     *
     * @param userId the user ID
     * @return the cart data, or null if not present or expired
     */
    public CartData getCart(Long userId) {
        CartData cart = cartCache.getIfPresent(userId);
        if (cart != null) {
            log.debug("Cart cache hit for userId={}", userId);
        } else {
            log.debug("Cart cache miss for userId={}", userId);
        }
        return cart;
    }

    /**
     * Stores or updates the cart for the given user in the cache.
     *
     * @param cart the cart data to store
     */
    public void saveCart(CartData cart) {
        cart.setUpdatedAt(java.time.LocalDateTime.now());
        cartCache.put(cart.getUserId(), cart);
        log.debug("Cart cached for userId={}", cart.getUserId());
    }

    /**
     * Removes the cart for the given user from the cache.
     *
     * @param userId the user ID
     */
    public void removeCart(Long userId) {
        cartCache.invalidate(userId);
        log.debug("Cart cache invalidated for userId={}", userId);
    }
}
