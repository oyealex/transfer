package com.ecommerce.cart.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CartCacheManager")
class CartCacheManagerTest {

    private CartCacheManager cartCacheManager;
    private Cache<Long, CartData> cache;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder()
                .maximumSize(100)
                .build();
        cartCacheManager = new CartCacheManager(cache);
    }

    @Test
    @DisplayName("getCart returns null when entry is not in cache")
    void testGetCart_notInCache_returnsNull() {
        CartData result = cartCacheManager.getCart(USER_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("saveCart then getCart returns the same data")
    void testSaveAndGetCart_returnsSameData() {
        CartData cartData = new CartData(USER_ID);
        CartItemData item = new CartItemData(100L, "Test SKU", new BigDecimal("25.00"), 3);
        cartData.getItems().add(item);

        cartCacheManager.saveCart(cartData);

        CartData retrieved = cartCacheManager.getCart(USER_ID);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getUserId()).isEqualTo(USER_ID);
        assertThat(retrieved.getItems()).hasSize(1);
        assertThat(retrieved.getItems().get(0).getSkuId()).isEqualTo(100L);
        assertThat(retrieved.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(retrieved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("cache TTL evicts entries after expiration period")
    void testCacheExpiry_ttl7days() throws InterruptedException {
        // Use a short TTL to verify the expiry mechanism works
        Cache<Long, CartData> shortTtlCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(200))
                .maximumSize(100)
                .build();
        CartCacheManager shortTtlManager = new CartCacheManager(shortTtlCache);

        CartData cartData = new CartData(USER_ID);
        cartData.getItems().add(new CartItemData(100L, "SKU", BigDecimal.TEN, 1));
        shortTtlManager.saveCart(cartData);

        // Immediately after save, the entry should be present
        CartData immediateResult = shortTtlManager.getCart(USER_ID);
        assertThat(immediateResult).isNotNull();

        // Wait for TTL to expire (200ms + buffer)
        Thread.sleep(300);

        // After TTL expiry, the entry should be evicted
        CartData expiredResult = shortTtlManager.getCart(USER_ID);
        assertThat(expiredResult).isNull();
    }

    @Test
    @DisplayName("removeCart clears the entry so getCart returns null afterwards")
    void testRemoveCart_clearsEntry() {
        CartData cartData = new CartData(USER_ID);
        cartData.getItems().add(new CartItemData(100L, "SKU", BigDecimal.TEN, 1));
        cartCacheManager.saveCart(cartData);

        // Verify entry exists
        assertThat(cartCacheManager.getCart(USER_ID)).isNotNull();

        // Remove the entry
        cartCacheManager.removeCart(USER_ID);

        // Verify entry is gone
        assertThat(cartCacheManager.getCart(USER_ID)).isNull();
    }

    @Test
    @DisplayName("saving cart updates the userId and preserves items")
    void testSaveCart_updatesData() {
        CartData cartData1 = new CartData(USER_ID);
        cartData1.getItems().add(new CartItemData(100L, "First SKU", BigDecimal.TEN, 1));
        cartCacheManager.saveCart(cartData1);

        CartData cartData2 = new CartData(USER_ID);
        cartData2.getItems().add(new CartItemData(200L, "Second SKU", new BigDecimal("20.00"), 5));
        cartCacheManager.saveCart(cartData2);

        CartData retrieved = cartCacheManager.getCart(USER_ID);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getUserId()).isEqualTo(USER_ID);
        assertThat(retrieved.getItems()).hasSize(1);
        assertThat(retrieved.getItems().get(0).getSkuId()).isEqualTo(200L);
        assertThat(retrieved.getItems().get(0).getQuantity()).isEqualTo(5);
    }
}
