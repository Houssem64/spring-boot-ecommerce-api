package com.example.ecommerce.service;

import com.example.ecommerce.dto.CartItemRequest;
import com.example.ecommerce.dto.CartItemResponse;
import com.example.ecommerce.dto.CartResponse;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartResponse getCart(String userEmail) {
        Cart cart = getOrCreateCart(userEmail);
        return mapToCartResponse(cart);
    }

    public CartResponse addToCart(String userEmail, CartItemRequest request) {
        Cart cart = getOrCreateCart(userEmail);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Find existing cart item or create new one
        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), product.getId())
                .map(existingItem -> {
                    existingItem.setQuantity(existingItem.getQuantity() + request.getQuantity());
                    existingItem.calculateSubtotal();
                    return cartItemRepository.save(existingItem);
                })
                .orElseGet(() -> {
                    CartItem newItem = CartItem.builder()
                            .cart(cart)
                            .product(product)
                            .quantity(request.getQuantity())
                            .unitPrice(product.getPrice())
                            .build();
                    newItem.calculateSubtotal();
                    return cartItemRepository.save(newItem);
                });

        // Update cart
        if (!cart.getItems().contains(cartItem)) {
            cart.getItems().add(cartItem);
        }
        cart.recalculateTotal();
        Cart savedCart = cartRepository.save(cart);
        
        return mapToCartResponse(savedCart);
    }

    public CartResponse updateCartItem(String userEmail, Long itemId, CartItemRequest request) {
        Cart cart = getOrCreateCart(userEmail);
        
        // First find the cart item
        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        // Update the quantity
        cartItem.setQuantity(request.getQuantity());
        cartItem.calculateSubtotal();
        cartItemRepository.save(cartItem);

        // Update cart total
        cart.recalculateTotal();
        Cart savedCart = cartRepository.save(cart);
        
        return mapToCartResponse(savedCart);
    }

    public CartResponse removeFromCart(String userEmail, Long itemId) {
        Cart cart = getOrCreateCart(userEmail);
        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (!cartItem.getCart().getId().equals(cart.getId())) {
            throw new IllegalStateException("Cart item does not belong to user's cart");
        }

        cart.getItems().remove(cartItem);
        cartItemRepository.delete(cartItem);
        cart.recalculateTotal();
        Cart savedCart = cartRepository.save(cart);
        
        return mapToCartResponse(savedCart);
    }

    public void clearCart(String userEmail) {
        Cart cart = getOrCreateCart(userEmail);
        cartItemRepository.deleteAll(cart.getItems());
        cart.getItems().clear();
        cart.setTotalAmount(BigDecimal.ZERO);
        cartRepository.save(cart);
    }

    private Cart getOrCreateCart(String userEmail) {
        return cartRepository.findByUserEmail(userEmail)
                .orElseGet(() -> {
                    User user = userRepository.findByEmail(userEmail)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                    Cart newCart = Cart.builder()
                            .user(user)
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    private CartResponse mapToCartResponse(Cart cart) {
        return CartResponse.builder()
                .id(cart.getId())
                .totalAmount(cart.getTotalAmount())
                .items(cart.getItems().stream()
                        .map(this::mapToCartItemResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private CartItemResponse mapToCartItemResponse(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
} 