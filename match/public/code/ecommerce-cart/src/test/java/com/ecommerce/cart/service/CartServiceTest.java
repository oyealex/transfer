package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartEstimateRequest;
import com.ecommerce.cart.dto.CartEstimateResponse;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.repository.CartItemRepository;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.query.InventoryQueryService;
import com.ecommerce.product.query.ProductQueryService;
import com.ecommerce.product.query.SkuDto;
import com.ecommerce.product.query.StockSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CartService")
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductQueryService productQueryService;

    @Mock
    private InventoryQueryService inventoryQueryService;

    @Mock
    private CartValidationService cartValidationService;

    @InjectMocks
    private CartService cartService;

    private static final Long USER_ID = 1L;
    private static final Long SKU_ID = 100L;
    private static final Long CART_ID = 10L;

    private SkuDto skuDto;
    private StockSummaryDto stockSummaryDto;

    @BeforeEach
    void setUp() {
        skuDto = new SkuDto();
        skuDto.setSkuId(SKU_ID);
        skuDto.setName("Test SKU");
        skuDto.setPrice(new BigDecimal("25.00"));
        skuDto.setStatus("ON_SHELF");

        stockSummaryDto = new StockSummaryDto(100, 0);
    }

    @Test
    @DisplayName("addItem creates a new CartItem when SKU is not already in cart")
    void testAddItem_newSku_createsCartItem() {
        AddCartItemRequest request = new AddCartItemRequest(SKU_ID, 3);
        Cart cart = new Cart(USER_ID);
        cart.setId(CART_ID);

        doNothing().when(cartValidationService).validateQuantity(3);
        when(cartValidationService.validateSku(SKU_ID)).thenReturn(skuDto);
        doNothing().when(cartValidationService).validateStock(SKU_ID, 3);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(Collections.emptyList());
        doNothing().when(cartValidationService).validateCartSize(0, 1);
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartItemResponse response = cartService.addItem(USER_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.getSkuId()).isEqualTo(SKU_ID);
        assertThat(response.getSkuName()).isEqualTo("Test SKU");
        assertThat(response.getQuantity()).isEqualTo(3);
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(response.getSubtotal()).isNotNull();
    }

    @Test
    @DisplayName("adding same SKU updates quantity")
    void testAddItem_existingSku_replacesQuantity() {
        // First: add item with quantity 3
        Cart cart = new Cart(USER_ID);
        cart.setId(CART_ID);

        AddCartItemRequest firstRequest = new AddCartItemRequest(SKU_ID, 3);

        doNothing().when(cartValidationService).validateQuantity(3);
        when(cartValidationService.validateSku(SKU_ID)).thenReturn(skuDto);
        doNothing().when(cartValidationService).validateStock(SKU_ID, 3);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(Collections.emptyList());
        doNothing().when(cartValidationService).validateCartSize(0, 1);
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartItemResponse firstResponse = cartService.addItem(USER_ID, firstRequest);
        assertThat(firstResponse.getQuantity()).isEqualTo(3);

        // Now the existing item is in the cart
        CartItem existingItem = new CartItem();
        existingItem.setCart(cart);
        existingItem.setSkuId(SKU_ID);
        existingItem.setSkuName("Test SKU");
        existingItem.setPrice(new BigDecimal("25.00"));
        existingItem.setQuantity(3);

        List<CartItem> existingItems = List.of(existingItem);

        // Second: add SAME SKU with quantity 2
        AddCartItemRequest secondRequest = new AddCartItemRequest(SKU_ID, 2);

        doNothing().when(cartValidationService).validateQuantity(2);
        when(cartValidationService.validateSku(SKU_ID)).thenReturn(skuDto);
        doNothing().when(cartValidationService).validateStock(SKU_ID, 2);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(existingItems);

        CartItemResponse secondResponse = cartService.addItem(USER_ID, secondRequest);

        // Verify final quantity after adding the same SKU again.
        assertThat(secondResponse.getQuantity()).isEqualTo(2);
        assertThat(secondResponse.getQuantity()).isNotEqualTo(5);
    }

    @Test
    @DisplayName("getCart returns all items with computed totalItems and totalAmount")
    void testGetCart_returnsAllItemsWithTotals() {
        Cart cart = new Cart(USER_ID);
        cart.setId(CART_ID);

        CartItem item1 = new CartItem();
        item1.setCart(cart);
        item1.setSkuId(100L);
        item1.setSkuName("Item A");
        item1.setPrice(new BigDecimal("10.00"));
        item1.setQuantity(2);

        CartItem item2 = new CartItem();
        item2.setCart(cart);
        item2.setSkuId(200L);
        item2.setSkuName("Item B");
        item2.setPrice(new BigDecimal("15.00"));
        item2.setQuantity(1);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(item1, item2));

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getTotalItems()).isEqualTo(3); // 2 + 1
        // totalAmount = 10*2 + 15*1 = 35.00
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("35.00"));

        CartItemResponse firstItem = response.getItems().get(0);
        assertThat(firstItem.getSkuId()).isEqualTo(100L);
        assertThat(firstItem.getSkuName()).isEqualTo("Item A");
        assertThat(firstItem.getQuantity()).isEqualTo(2);
        assertThat(firstItem.getSubtotal()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("updateItem changes the quantity of an existing cart item")
    void testUpdateItem_changesQuantity() {
        Cart cart = new Cart(USER_ID);
        cart.setId(CART_ID);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setSkuId(SKU_ID);
        item.setSkuName("Test SKU");
        item.setPrice(new BigDecimal("25.00"));
        item.setQuantity(1);

        UpdateCartItemRequest request = new UpdateCartItemRequest(5);

        doNothing().when(cartValidationService).validateQuantity(5);
        doNothing().when(cartValidationService).validateStock(SKU_ID, 5);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(item));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartItemResponse response = cartService.updateItem(USER_ID, SKU_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getSkuId()).isEqualTo(SKU_ID);
    }

    @Test
    @DisplayName("removeItem deletes the item from the cart")
    void testRemoveItem_deletesItem() {
        Cart cart = new Cart(USER_ID);
        cart.setId(CART_ID);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setSkuId(SKU_ID);
        item.setSkuName("Test SKU");
        item.setPrice(new BigDecimal("25.00"));
        item.setQuantity(1);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(item));

        cartService.removeItem(USER_ID, SKU_ID);

        verify(cartItemRepository).delete(item);
    }

    @Test
    @DisplayName("removeItem throws ResourceNotFoundException when cart does not exist")
    void testRemoveItem_cartNotFound_throwsException() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, SKU_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("clearCart removes all items from the existing cart")
    void testClearCart_removesAllItems() {
        Cart cart = new Cart(USER_ID);
        cart.setId(CART_ID);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));

        cartService.clearCart(USER_ID);

        verify(cartItemRepository).deleteByCartId(CART_ID);
    }

    @Test
    @DisplayName("clearCart does nothing when cart does not exist")
    void testClearCart_noCart_doesNothing() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        cartService.clearCart(USER_ID);

        verify(cartItemRepository, never()).deleteByCartId(anyLong());
    }

    @Test
    @DisplayName("estimate calculates correct total with shippingFee 8.00 and packagingFee 2.00 when itemTotal < 199")
    void testEstimate_calculatesCorrectTotal() {
        Cart cart = new Cart(USER_ID);
        cart.setId(CART_ID);

        CartItem item1 = new CartItem();
        item1.setCart(cart);
        item1.setSkuId(100L);
        item1.setSkuName("Item A");
        item1.setPrice(new BigDecimal("10.00"));
        item1.setQuantity(2);

        CartItem item2 = new CartItem();
        item2.setCart(cart);
        item2.setSkuId(200L);
        item2.setSkuName("Item B");
        item2.setPrice(new BigDecimal("15.00"));
        item2.setQuantity(1);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(item1, item2));

        SkuDto skuDto1 = new SkuDto();
        skuDto1.setSkuId(100L);
        skuDto1.setPrice(new BigDecimal("50.00"));
        SkuDto skuDto2 = new SkuDto();
        skuDto2.setSkuId(200L);
        skuDto2.setPrice(new BigDecimal("30.00"));

        when(productQueryService.getSkuForSale(100L)).thenReturn(skuDto1);
        when(productQueryService.getSkuForSale(200L)).thenReturn(skuDto2);

        CartEstimateRequest request = new CartEstimateRequest();
        CartEstimateResponse response = cartService.estimate(USER_ID, request);

        // itemTotal = 50*2 + 30*1 = 130.00
        assertThat(response.getItemTotal()).isEqualByComparingTo(new BigDecimal("130.00"));
        // shipping = 8.00 (since 130 < 199)
        assertThat(response.getShippingFee()).isEqualByComparingTo(new BigDecimal("8.00"));
        // packaging = 2.00
        assertThat(response.getPackagingFee()).isEqualByComparingTo(new BigDecimal("2.00"));
        // discount = 0
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // points = 0
        assertThat(response.getPointsDeductionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // payable = 130 + 8 + 2 = 140.00
        assertThat(response.getPayableAmount()).isEqualByComparingTo(new BigDecimal("140.00"));
    }

    @Test
    @DisplayName("estimate applies free shipping when itemTotal >= 199")
    void testEstimate_freeShippingOver199() {
        Cart cart = new Cart(USER_ID);
        cart.setId(CART_ID);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setSkuId(100L);
        item.setSkuName("Expensive Item");
        item.setPrice(new BigDecimal("100.00"));
        item.setQuantity(2);

        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(List.of(item));

        SkuDto skuDtoExpensive = new SkuDto();
        skuDtoExpensive.setSkuId(100L);
        skuDtoExpensive.setPrice(new BigDecimal("100.00"));
        when(productQueryService.getSkuForSale(100L)).thenReturn(skuDtoExpensive);

        CartEstimateRequest request = new CartEstimateRequest();
        CartEstimateResponse response = cartService.estimate(USER_ID, request);

        // itemTotal = 100*2 = 200.00
        assertThat(response.getItemTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        // shipping = 0 (since 200 >= 199)
        assertThat(response.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        // packaging = 2.00
        assertThat(response.getPackagingFee()).isEqualByComparingTo(new BigDecimal("2.00"));
        // payable = 200 + 0 + 2 = 202.00
        assertThat(response.getPayableAmount()).isEqualByComparingTo(new BigDecimal("202.00"));
    }

    @Test
    @DisplayName("estimate returns zero for empty cart")
    void testEstimate_emptyCart_returnsZero() {
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        CartEstimateRequest request = new CartEstimateRequest();
        CartEstimateResponse response = cartService.estimate(USER_ID, request);

        assertThat(response.getItemTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPackagingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPayableAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("CartService persists cart data")
    void testCart_storedInDatabase() {
        AddCartItemRequest request = new AddCartItemRequest(SKU_ID, 1);
        Cart cart = new Cart(USER_ID);
        cart.setId(CART_ID);

        doNothing().when(cartValidationService).validateQuantity(1);
        when(cartValidationService.validateSku(SKU_ID)).thenReturn(skuDto);
        doNothing().when(cartValidationService).validateStock(SKU_ID, 1);
        when(cartRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);
        when(cartItemRepository.findByCartId(CART_ID)).thenReturn(Collections.emptyList());
        doNothing().when(cartValidationService).validateCartSize(0, 1);
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cartService.addItem(USER_ID, request);

        // Verify persistence interactions.
        verify(cartRepository).findByUserId(USER_ID);
        verify(cartRepository).save(any(Cart.class));
        verify(cartItemRepository).findByCartId(CART_ID);
        verify(cartItemRepository).save(any(CartItem.class));
    }
}
