package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartEstimateRequest;
import com.ecommerce.cart.dto.CartEstimateResponse;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.entity.CartStatus;
import com.ecommerce.cart.repository.CartItemRepository;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.money.MonetaryUtil;
import com.ecommerce.product.query.InventoryQueryService;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Core service for shopping cart operations.
 *
 * <p>Uses JPA entities ({@link Cart}, {@link CartItem}) and repositories
 * ({@link CartRepository}, {@link CartItemRepository}) to persist cart data to H2.
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private static final BigDecimal SHIPPING_FEE = new BigDecimal("8.00");
    private static final BigDecimal PACKAGING_FEE = new BigDecimal("2.00");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("199.00");

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductQueryService productQueryService;
    private final InventoryQueryService inventoryQueryService;
    private final CartValidationService cartValidationService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductQueryService productQueryService,
                       InventoryQueryService inventoryQueryService,
                       CartValidationService cartValidationService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productQueryService = productQueryService;
        this.inventoryQueryService = inventoryQueryService;
        this.cartValidationService = cartValidationService;
    }

    /**
     * Adds an item to the user's cart. If the cart does not exist, it is created.
     */
    @Transactional
    public CartItemResponse addItem(Long userId, AddCartItemRequest request) {
        log.debug("Adding item to cart: userId={}, skuId={}, quantity={}",
                userId, request.getSkuId(), request.getQuantity());

        // Validate quantity range
        cartValidationService.validateQuantity(request.getQuantity());

        // Validate SKU status (ON_SHELF) and get SKU details
        SkuDto sku = cartValidationService.validateSku(request.getSkuId());

        // Validate stock availability
        cartValidationService.validateStock(request.getSkuId(), request.getQuantity());

        // Get or create cart
        Cart cart = getOrCreateCart(userId);

        // Check if this SKU already exists in the cart
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        Optional<CartItem> existingItem = items.stream()
                .filter(item -> item.getSkuId().equals(request.getSkuId()))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(request.getQuantity());
            cartItemRepository.save(item);

            BigDecimal subtotal = MonetaryUtil.multiply(item.getPrice(),
                    BigDecimal.valueOf(item.getQuantity()));
            log.debug("Updated existing item: skuId={}, newQuantity={}", request.getSkuId(), item.getQuantity());
            return new CartItemResponse(item.getSkuId(), item.getSkuName(), item.getPrice(),
                    item.getQuantity(), subtotal);
        } else {
            // Validate cart size for new item types
            cartValidationService.validateCartSize(items.size(), 1);

            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setSkuId(sku.getSkuId());
            newItem.setSkuName(sku.getName());
            newItem.setPrice(sku.getPrice());
            newItem.setQuantity(request.getQuantity());
            cartItemRepository.save(newItem);

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
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        log.debug("Getting cart for userId={}", userId);

        return cartRepository.findByUserId(userId)
                .map(cart -> {
                    List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
                    return buildCartResponse(items);
                })
                .orElse(buildEmptyCartResponse());
    }

    /**
     * Updates the quantity of an existing item in the cart.
     */
    @Transactional
    public CartItemResponse updateItem(Long userId, Long skuId, UpdateCartItemRequest request) {
        log.debug("Updating cart item: userId={}, skuId={}, quantity={}", userId, skuId, request.getQuantity());

        cartValidationService.validateQuantity(request.getQuantity());
        cartValidationService.validateStock(skuId, request.getQuantity());

        Cart cart = findCartByUserId(userId);
        CartItem item = findCartItemBySkuId(cart.getId(), skuId);

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        BigDecimal subtotal = MonetaryUtil.multiply(item.getPrice(),
                BigDecimal.valueOf(item.getQuantity()));
        log.debug("Updated item quantity: skuId={}, newQuantity={}", skuId, item.getQuantity());
        return new CartItemResponse(item.getSkuId(), item.getSkuName(), item.getPrice(),
                item.getQuantity(), subtotal);
    }

    /**
     * Removes a single item from the cart.
     */
    @Transactional
    public void removeItem(Long userId, Long skuId) {
        log.debug("Removing item from cart: userId={}, skuId={}", userId, skuId);

        Cart cart = findCartByUserId(userId);
        CartItem item = findCartItemBySkuId(cart.getId(), skuId);

        cartItemRepository.delete(item);
        log.debug("Removed item: skuId={} from cart of userId={}", skuId, userId);
    }

    /**
     * Clears all items from the cart.
     */
    @Transactional
    public void clearCart(Long userId) {
        log.debug("Clearing cart for userId={}", userId);

        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        if (cartOpt.isPresent()) {
            cartItemRepository.deleteByCartId(cartOpt.get().getId());
            log.debug("Cart cleared for userId={}", userId);
        }
    }

    /**
     * Estimates the total price for the cart including shipping and fees.
     * Uses ProductQueryService for current SKU prices.
     *
     * <p>Discount calculation via PromotionCalculationService is not yet integrated;
     * discountAmount and pointsDeductionAmount are returned as zero.
     */
    @Transactional(readOnly = true)
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

        // Shipping fee: 8.00, free if itemTotal >= 199.00 (correct boundary: >=)
        BigDecimal shippingFee = itemTotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO : SHIPPING_FEE;

        // Packaging fee: always 2.00
        BigDecimal packagingFee = PACKAGING_FEE;

        // Discount and points deduction — placeholder (requires PromotionCalculationService)
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

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart cart = new Cart(userId);
                    cart = cartRepository.save(cart);
                    log.debug("Created new cart for userId={}, cartId={}", userId, cart.getId());
                    return cart;
                });
    }

    private Cart findCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart for user " + userId + " not found"));
    }

    private CartItem findCartItemBySkuId(Long cartId, Long skuId) {
        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        return items.stream()
                .filter(item -> item.getSkuId().equals(skuId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CartItem for skuId " + skuId + " not found in cart " + cartId));
    }

    private CartResponse buildCartResponse(List<CartItem> items) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItem item : items) {
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
