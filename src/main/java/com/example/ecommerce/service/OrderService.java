package com.example.ecommerce.service;

import com.example.ecommerce.dto.*;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.model.*;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public OrderResponse createOrder(OrderRequest request, String userEmail) {
        try {
            logger.info("Creating order for user: {}", userEmail);
            
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            
            logger.info("Found user: {}", user.getEmail());

            Order order = Order.builder()
                    .user(user)
                    .orderDate(LocalDateTime.now())
                    .status(OrderStatus.PENDING)
                    .orderItems(new ArrayList<>())
                    .totalAmount(BigDecimal.ZERO)
                    .build();

            BigDecimal total = BigDecimal.ZERO;
            
            logger.info("Processing {} order items", request.getItems().size());
            
            for (OrderItemRequest itemRequest : request.getItems()) {
                Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + itemRequest.getProductId()));

                if (product.getStockQuantity() < itemRequest.getQuantity()) {
                    throw new IllegalStateException(
                        "Insufficient stock for product: " + product.getName());
                }

                BigDecimal subtotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
                
                OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .build();
                
                order.addOrderItem(orderItem);
                total = total.add(subtotal);
                
                product.setStockQuantity(product.getStockQuantity() - itemRequest.getQuantity());
                productRepository.save(product);
            }

            order.setTotalAmount(total);
            
            Order savedOrder = orderRepository.save(order);
            logger.info("Order created successfully with ID: {}", savedOrder.getId());

            return mapToOrderResponse(savedOrder);
            
        } catch (Exception e) {
            logger.error("Error creating order: ", e);
            throw e;
        }
    }

    public List<OrderResponse> getUserOrders(String userEmail) {
        return orderRepository.findByUserEmailOrderByOrderDateDesc(userEmail).stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrder(Long id, String userEmail) {
        Order order = orderRepository.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return mapToOrderResponse(order);
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setStatus(newStatus);
        return mapToOrderResponse(orderRepository.save(order));
    }

    public OrderResponse cancelOrder(Long id, String userEmail) {
        Order order = orderRepository.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Can only cancel pending orders");
        }

        order.setStatus(OrderStatus.CANCELLED);
        restoreProductStock(order.getOrderItems());
        return mapToOrderResponse(orderRepository.save(order));
    }

    private void restoreProductStock(List<OrderItem> orderItems) {
        orderItems.forEach(item -> {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        });
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(this::mapToOrderItemResponse)
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .userEmail(order.getUser().getEmail())
                .items(itemResponses)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .build();
    }

    private OrderItemResponse mapToOrderItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
} 