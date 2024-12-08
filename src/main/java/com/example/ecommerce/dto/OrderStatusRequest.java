package com.example.ecommerce.dto;

import com.example.ecommerce.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusRequest {
    private OrderStatus status;
} 