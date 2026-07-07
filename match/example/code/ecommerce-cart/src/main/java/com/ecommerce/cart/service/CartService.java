package com.ecommerce.cart.service;

import com.ecommerce.cart.cache.CartCacheManager;
import com.ecommerce.cart.cache.CartData;
import com.ecommerce.cart.cache.CartItemData;
import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartEstimateRequest;
import com.ecommerce.cart.dto.CartEstimateResponse;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.product.query.InventoryQueryService;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core service for shopping cart operations.
 *
 * <p>Uses Caffeine Cache via {@link CartCacheManager} to store cart data
 * with a 7-day TTL. Cart data is NOT persisted to the database.
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private static final BigDecimal SHIPPING_FEE = new BigDecimal("8.00");
    private static final BigDecimal PACKAGING_FEE = new BigDecimal("2.00");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("199.00");

    private final CartCacheManager cartCacheManager;
    private final ProductQueryService productQueryService;
    private final InventoryQueryService inventoryQueryService;
    private final CartValidationService cartValidationService;

    public CartService(CartCacheManager cartCacheManager,
                       ProductQueryService productQueryService,
                       InventoryQueryService inventoryQueryService,
                       CartValidationService cartValidationService) {
        this.cartCacheManager = cartCacheManager;
        this.productQueryService = productQueryService;
        this.inventoryQueryService = inventoryQueryService;
        this.cartValidationService = cartValidationService;
    }

    /**
     * Adds an item to the user's cart. If the cart does not exist, it is created.
     * When the same SKU already exists in the cart, the quantity is accumulated.
     */
    public CartItemResponse addItem(Long userId, AddCartItemRequest request) {
        log.debug("Adding item to cart: userId={}, skuId={}, quantity={}",
                userId, request.getSkuId(), request.getQuantity());

        // Validate quantity range
        cartValidationService.validateQuantity(request.getQuantity());

        // Validate SKU status (ON_SHELF) and get SKU details
        SkuDto sku = cartValidationService.validateSku(request.getSkuId());

        // Get or create cart from cache
        CartData cart = getOrCreateCart(userId);

        // Check if this SKU already exists in the cart
        Optional<CartItemData> existingItem = cart.getItems().stream()
                .filter(item -> item.getSkuId().equals(request.getSkuId()))
                .findFirst();

        if (existingItem.isPresent()) {
            // L2-11 fix: accumulate quantity instead of overwriting
            CartItemData item = existingItem.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            cartCacheManager.saveCart(cart);

            BigDecimal subtotal = MonetaryUtil.multiply(item.getPrice(),
                    BigDecimal.valueOf(item.getQuantity()));
            log.debug("Updated existing item: skuId={}, newQuantity={}", request.getSkuId(), item.getQuantity());
            return new CartItemResponse(item.getSkuId(), item.getSkuName(), item.getPrice(),
                    item.getQuantity(), subtotal);
        } else {
            // Validate cart size for new item types
            cartValidationService.validateCartSize(cart.getItems().size(), 1);

            // Validate stock availability
            cartValidationService.validateStock(request.getSkuId(), request.getQuantity());

            CartItemData newItem = new CartItemData(request.getSkuId(), sku.getName(),
                    sku.getPrice(), request.getQuantity());
            cart.getItems().add(newItem);
            cartCacheManager.saveCart(cart);

            BigDecimal subtotal = MonetaryUtil.multiply(sku.getPrice(),
                    BigDecimal.valueOf(request.getQuantity()));
            log.debug("Added new item: skuId={}, quantity={}", request.getSkuId(), request.getQuantity());
            return new CartItemResponse(newItem.getSkuId(), newItem.getSkuName(), newItem.getPrice(),
                    newItem.getQuantity(), subtotal);
        }
    }

    /**
     * Retrieves the full cart for the given user.
     */
    public CartResponse getCart(Long userId) {
        log.debug("Getting cart for userId={}", userId);

        CartData cart = cartCacheManager.getCart(userId);
        if (cart == null || cart.getItems().isEmpty()) {
            return buildEmptyCartResponse();
        }
        return buildCartResponse(cart.getItems());
    }

    /**
     * Updates the quantity of an existing item in the cart.
     */
    public CartItemResponse updateItem(Long userId, Long skuId, UpdateCartItemRequest request) {
        log.debug("Updating cart item: userId={}, skuId={}, quantity={}", userId, skuId, request.getQuantity());

        cartValidationService.validateQuantity(request.getQuantity());
        cartValidationService.validateStock(skuId, request.getQuantity());

        CartData cart = findCartByUserId(userId);
        CartItemData item = findCartItemBySkuId(cart, skuId);

        item.setQuantity(request.getQuantity());
        cartCacheManager.saveCart(cart);

        BigDecimal subtotal = MonetaryUtil.multiply(item.getPrice(),
                BigDecimal.valueOf(item.getQuantity()));
        log.debug("Updated item quantity: skuId={}, newQuantity={}", skuId, item.getQuantity());
        return new CartItemResponse(item.getSkuId(), item.getSkuName(), item.getPrice(),
                item.getQuantity(), subtotal);
    }

    /**
     * Removes a single item from the cart.
     */
    public void removeItem(Long userId, Long skuId) {
        log.debug("Removing item from cart: userId={}, skuId={}", userId, skuId);

        CartData cart = findCartByUserId(userId);
        CartItemData item = findCartItemBySkuId(cart, skuId);

        cart.getItems().remove(item);
        cartCacheManager.saveCart(cart);
        log.debug("Removed item: skuId={} from cart of userId={}", skuId, userId);
    }

    /**
     * Clears all items from the cart.
     */
    public void clearCart(Long userId) {
        log.debug("Clearing cart for userId={}", userId);

        cartCacheManager.removeCart(userId);
        log.debug("Cart cleared for userId={}", userId);
    }

    /**
     * Estimates the total price for the cart including shipping and fees.
     */
    public CartEstimateResponse estimate(Long userId, CartEstimateRequest request) {
        log.debug("Estimating cart for userId={}, couponIds={}, redeemPoints={}",
                userId, request.getCouponIds(), request.getRedeemPoints());

        CartResponse cart = getCart(userId);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            CartEstimateResponse empty = new CartEstimateResponse();
            empty.setItemTotal(BigDecimal.ZERO);
            empty.setShippingFee(BigDecimal.ZERO);
            empty.setPackagingFee(BigDecimal.ZERO);
            empty.setDiscountAmount(BigDecimal.ZERO);
            empty.setPointsDeductionAmount(BigDecimal.ZERO);
            empty.setPayableAmount(BigDecimal.ZERO);
            return empty;
        }

        // Calculate item total using current SKU prices from ProductQueryService
        BigDecimal itemTotal = BigDecimal.ZERO;
        for (CartItemResponse item : cart.getItems()) {
            SkuDto sku = productQueryService.getSkuForSale(item.getSkuId());
            if (sku == null) {
                throw new BusinessException("SKU_NOT_FOUND",
                        "SKU " + item.getSkuId() + " is no longer available");
            }
            BigDecimal lineTotal = MonetaryUtil.multiply(sku.getPrice(),
                    BigDecimal.valueOf(item.getQuantity()));
            itemTotal = MonetaryUtil.add(itemTotal, lineTotal);
        }

        // Shipping fee: 8.00, free if itemTotal >= 199.00
        BigDecimal shippingFee = itemTotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO : SHIPPING_FEE;

        // Packaging fee: always 2.00
        BigDecimal packagingFee = PACKAGING_FEE;

        // Discount and points deduction — placeholder
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal pointsDeductionAmount = BigDecimal.ZERO;

        // Payable amount = itemTotal + shippingFee + packagingFee - discountAmount - pointsDeduction
        BigDecimal payableAmount = MonetaryUtil.add(itemTotal, shippingFee);
        payableAmount = MonetaryUtil.add(payableAmount, packagingFee);
        payableAmount = MonetaryUtil.subtract(payableAmount, discountAmount);
        payableAmount = MonetaryUtil.subtract(payableAmount, pointsDeductionAmount);

        if (payableAmount.compareTo(BigDecimal.ZERO) < 0) {
            payableAmount = BigDecimal.ZERO;
        }

        CartEstimateResponse response = new CartEstimateResponse();
        response.setItemTotal(itemTotal);
        response.setShippingFee(shippingFee);
        response.setPackagingFee(packagingFee);
        response.setDiscountAmount(discountAmount);
        response.setPointsDeductionAmount(pointsDeductionAmount);
        response.setPayableAmount(payableAmount);

        log.debug("Cart estimate: itemTotal={}, shipping={}, packaging={}, payable={}",
                itemTotal, shippingFee, packagingFee, payableAmount);
        return response;
    }

    // ---- private helpers ----

    private CartData getOrCreateCart(Long userId) {
        CartData cart = cartCacheManager.getCart(userId);
        if (cart == null) {
            cart = new CartData(userId);
            cartCacheManager.saveCart(cart);
            log.debug("Created new cart in cache for userId={}", userId);
        }
        return cart;
    }

    private CartData findCartByUserId(Long userId) {
        CartData cart = cartCacheManager.getCart(userId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart for user " + userId + " not found");
        }
        return cart;
    }

    private CartItemData findCartItemBySkuId(CartData cart, Long skuId) {
        return cart.getItems().stream()
                .filter(item -> item.getSkuId().equals(skuId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CartItem for skuId " + skuId + " not found in cart"));
    }

    private CartResponse buildCartResponse(List<CartItemData> items) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItemData item : items) {
            BigDecimal subtotal = MonetaryUtil.multiply(item.getPrice(),
                    BigDecimal.valueOf(item.getQuantity()));
            CartItemResponse itemResponse = new CartItemResponse(
                    item.getSkuId(), item.getSkuName(), item.getPrice(),
                    item.getQuantity(), subtotal);
            itemResponses.add(itemResponse);
            totalItems += item.getQuantity();
            totalAmount = MonetaryUtil.add(totalAmount, subtotal);
        }

        return new CartResponse(itemResponses, totalItems, totalAmount);
    }

    private CartResponse buildEmptyCartResponse() {
        return new CartResponse(new ArrayList<>(), 0, BigDecimal.ZERO);
    }
}
