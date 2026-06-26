package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartEstimateRequest;
import com.ecommerce.cart.dto.CartEstimateResponse;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.service.CartService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart REST controller — all endpoints require USER role.
 */
@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasRole('USER')")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * Adds a SKU to the user's cart.
     */
    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addItem(@Valid @RequestBody AddCartItemRequest request) {
        Long userId = getCurrentUserId();
        log.debug("POST /api/v1/cart/items: userId={}, skuId={}, quantity={}",
                userId, request.getSkuId(), request.getQuantity());
        CartItemResponse response = cartService.addItem(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves the full cart for the current user.
     */
    @GetMapping
    public ResponseEntity<CartResponse> getCart() {
        Long userId = getCurrentUserId();
        log.debug("GET /api/v1/cart: userId={}", userId);
        CartResponse response = cartService.getCart(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates the quantity of a specific item in the cart.
     */
    @PutMapping("/items/{skuId}")
    public ResponseEntity<CartItemResponse> updateItem(@PathVariable Long skuId,
                                                        @Valid @RequestBody UpdateCartItemRequest request) {
        Long userId = getCurrentUserId();
        log.debug("PUT /api/v1/cart/items/{}: userId={}, quantity={}", skuId, userId, request.getQuantity());
        CartItemResponse response = cartService.updateItem(userId, skuId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Removes a specific item from the cart.
     */
    @DeleteMapping("/items/{skuId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long skuId) {
        Long userId = getCurrentUserId();
        log.debug("DELETE /api/v1/cart/items/{}: userId={}", skuId, userId);
        cartService.removeItem(userId, skuId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clears all items from the cart.
     */
    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        Long userId = getCurrentUserId();
        log.debug("DELETE /api/v1/cart: userId={}", userId);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Estimates the total price for the cart including shipping and fees.
     */
    @PostMapping("/estimate")
    public ResponseEntity<CartEstimateResponse> estimate(@Valid @RequestBody CartEstimateRequest request) {
        Long userId = getCurrentUserId();
        log.debug("POST /api/v1/cart/estimate: userId={}", userId);
        CartEstimateResponse response = cartService.estimate(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Extracts the current user's ID from the Spring Security context.
     * Assumes the principal name is the userId for JWT-based authentication.
     */
    private Long getCurrentUserId() {
        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse user ID from principal '{}'", principal);
            throw new com.ecommerce.common.exception.AuthorizationException(
                    "UNAUTHORIZED", "Invalid user principal: " + principal);
        }
    }
}
