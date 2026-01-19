package com.tathang.example304.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tathang.example304.model.*;
import com.tathang.example304.repository.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order addItemToOrder(Long orderId, Long productId, Integer quantity) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getStockQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock");
        }

        List<OrderItem> existingItems = orderItemRepository.findByOrderId(orderId);

        Optional<OrderItem> existingItem = existingItems.stream()
                .filter(item -> item.getProduct() != null &&
                        item.getProduct().getId().equals(productId))
                .findFirst();

        if (existingItem.isPresent()) {
            OrderItem item = existingItem.get();
            item.setPrice(product.getPrice());
            item.setQuantity(item.getQuantity() + quantity);
            orderItemRepository.save(item);
        } else {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order); // 🔥 BẮT BUỘC
            orderItem.setProduct(product); // 🔥 BẮT BUỘC
            orderItem.setQuantity(quantity);
            orderItem.setPrice(product.getPrice()); // 🔥 BẮT BUỘC
            orderItemRepository.save(orderItem);
        }

        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);

        updateOrderTotal(orderId);

        return orderRepository.findById(orderId).orElse(null);
    }

    public Order updateOrderStatus(Long orderId, Order.OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        return updatedOrder;
    }

    public void updateOrderTotal(Long orderId) {
        Double total = orderItemRepository.getTotalAmountByOrderId(orderId);
        if (total != null) {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order != null) {
                order.setTotalAmount(BigDecimal.valueOf(total));
                orderRepository.save(order);
            }
        }
    }

    public List<Order> getPendingOrders() {
        return orderRepository.findByStatus(Order.OrderStatus.PENDING);
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng với ID: " + orderId));
    }

    public void deleteOrder(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new RuntimeException("Đơn hàng không tồn tại để xóa");
        }
        orderRepository.deleteById(orderId);
    }

    public Order updateOrderItemQuantity(Long orderId, Long productId, Integer quantity) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        OrderItem orderItem = orderItems.stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Order item not found"));

        Product product = orderItem.getProduct();

        // Tính toán chênh lệch số lượng
        int quantityDifference = quantity - orderItem.getQuantity();

        // Kiểm tra tồn kho
        if (product.getStockQuantity() < quantityDifference) {
            throw new RuntimeException("Insufficient stock");
        }

        // Cập nhật số lượng
        orderItem.setQuantity(quantity);
        orderItemRepository.save(orderItem);

        // Cập nhật tồn kho
        product.setStockQuantity(product.getStockQuantity() - quantityDifference);
        productRepository.save(product);

        return order;
    }

    public Order removeItemFromOrder(Long orderId, Long productId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        OrderItem orderItem = orderItems.stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Order item not found"));

        // Hoàn trả tồn kho
        Product product = orderItem.getProduct();
        product.setStockQuantity(product.getStockQuantity() + orderItem.getQuantity());
        productRepository.save(product);

        // Xóa item
        orderItemRepository.delete(orderItem);
        return order;
    }

    public List<OrderItem> getOrderItemsByOrderId(Long orderId) {
        return orderItemRepository.findByOrderIdWithProduct(orderId);
    }

    public Order saveOrder(Order order) {
        return orderRepository.save(order);
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByuserId(userId);
    }

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

    }

}
