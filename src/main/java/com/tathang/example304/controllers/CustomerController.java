package com.tathang.example304.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.tathang.example304.model.*;
import com.tathang.example304.model.Order.OrderStatus;
import com.tathang.example304.payload.request.PaymentRequest;
import com.tathang.example304.repository.BillRepository;
import com.tathang.example304.repository.OrderRepository;
import com.tathang.example304.security.services.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/customer")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CustomerController {

    private final OrderService orderService;
    private final ProductService productService;
    private final BillService billService;
    private final UserService userService;
    @Autowired
    private PayOSService payOSService;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private OrderRepository orderRepository;

    public CustomerController(OrderService orderService, ProductService productService,
            BillService billService, UserService userService) {
        this.orderService = orderService;
        this.productService = productService;
        this.billService = billService;
        this.userService = userService;
    }

    // === MENU ===
    @GetMapping("/menu")
    public ResponseEntity<List<Product>> getMenu() {
        List<Product> products = productService.getAvailableProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getCategories() {
        List<Category> categories = productService.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    // === GET PRODUCTS ===
    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAllProducts() {
        try {
            List<Product> products = productService.getAllProducts();
            return ResponseEntity.ok(products);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        try {
            Product product = productService.getProductById(id);
            return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/products/by-category/{categoryId}")
    public ResponseEntity<List<Product>> getProductsByCategory(@PathVariable Long categoryId) {
        List<Product> products = productService.getProductsByCategory(categoryId);
        return ResponseEntity.ok(products);
    }

    // === ORDER MANAGEMENT ===

    /**
     * Tạo order mới cho khách hàng
     */
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            System.out.println("🛒 Creating order for user: " + userDetails.getUsername());

            // Lấy user từ database
            User user = userService.getUserById(userDetails.getId());
            if (user == null) {
                return ResponseEntity.badRequest().body("User not found");
            }

            // Tạo order mới
            Order order = new Order(user);
            order.setStatus(Order.OrderStatus.PENDING);
            order.setTotalAmount(BigDecimal.ZERO);

            // Lưu order
            Order savedOrder = orderService.saveOrder(order);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Order created successfully");
            response.put("orderId", savedOrder.getId());
            response.put("status", savedOrder.getStatus());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error creating order: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to create order: " + e.getMessage());
        }
    }

    /**
     * Thêm sản phẩm vào order
     */
    @PostMapping("/orders/{orderId}/items")
    public ResponseEntity<?> addItemToOrder(
            @PathVariable Long orderId,
            @RequestBody OrderItemRequest itemRequest,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        try {
            System.out.println("➕ Adding item to order: " + orderId);
            System.out.println("   Product ID: " + itemRequest.getProductId());
            System.out.println("   Quantity: " + itemRequest.getQuantity());

            // Kiểm tra order thuộc về user
            Order order = orderService.getOrderById(orderId);
            if (order == null || !order.getUser().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Order not found or access denied");
            }

            // Kiểm tra sản phẩm tồn tại
            Product product = productService.getProductById(itemRequest.getProductId());
            if (product == null) {
                return ResponseEntity.badRequest().body("Product not found");
            }

            // Kiểm tra tồn kho
            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                return ResponseEntity.badRequest().body("Insufficient stock");
            }

            // Thêm sản phẩm vào order
            Order updatedOrder = orderService.addItemToOrder(
                    orderId, itemRequest.getProductId(), itemRequest.getQuantity());

            // Lấy thông tin order items
            List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(orderId);

            // Tính tổng tiền
            BigDecimal totalAmount = orderItems.stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Cập nhật tổng tiền
            updatedOrder.setTotalAmount(totalAmount);
            orderService.saveOrder(updatedOrder);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Item added successfully");
            response.put("orderId", orderId);
            response.put("totalAmount", totalAmount);
            response.put("items", orderItems);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error adding item to order: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to add item: " + e.getMessage());
        }
    }

    /**
     * Xem chi tiết order
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrderDetails(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        try {
            System.out.println("📋 Getting order details: " + orderId);

            // Kiểm tra order thuộc về user
            Order order = orderService.getOrderById(orderId);
            if (order == null || !order.getUser().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Order not found or access denied");
            }

            // Lấy order items
            List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(orderId);

            Map<String, Object> response = new HashMap<>();
            response.put("order", order);
            response.put("items", orderItems);
            response.put("totalItems", orderItems.size());
            response.put("totalAmount", order.getTotalAmount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error getting order details: " + e.getMessage());
            return ResponseEntity.badRequest().body("Failed to get order details");
        }
    }

    /**
     * Xóa sản phẩm khỏi order
     */
    @DeleteMapping("/orders/{orderId}/items/{productId}")
    public ResponseEntity<?> removeItemFromOrder(
            @PathVariable Long orderId,
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        try {
            System.out.println("➖ Removing item from order: " + orderId);
            System.out.println("   Product ID: " + productId);

            // Kiểm tra order thuộc về user
            Order order = orderService.getOrderById(orderId);
            if (order == null || !order.getUser().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Order not found or access denied");
            }

            // Xóa sản phẩm khỏi order
            Order updatedOrder = orderService.removeItemFromOrder(orderId, productId);

            // Cập nhật tổng tiền
            List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(orderId);
            BigDecimal totalAmount = orderItems.stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            updatedOrder.setTotalAmount(totalAmount);
            orderService.saveOrder(updatedOrder);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Item removed successfully");
            response.put("orderId", orderId);
            response.put("totalAmount", totalAmount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error removing item from order: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to remove item: " + e.getMessage());
        }
    }

    /**
     * Cập nhật số lượng sản phẩm trong order
     */
    @PutMapping("/orders/{orderId}/items/{productId}")
    public ResponseEntity<?> updateItemQuantity(
            @PathVariable Long orderId,
            @PathVariable Long productId,
            @RequestParam Integer quantity,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        try {
            System.out.println("✏️ Updating item quantity in order: " + orderId);
            System.out.println("   Product ID: " + productId);
            System.out.println("   New quantity: " + quantity);

            // Kiểm tra order thuộc về user
            Order order = orderService.getOrderById(orderId);
            if (order == null || !order.getUser().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Order not found or access denied");
            }

            // Kiểm tra sản phẩm tồn tại và có đủ tồn kho
            Product product = productService.getProductById(productId);
            if (product == null) {
                return ResponseEntity.badRequest().body("Product not found");
            }

            if (product.getStockQuantity() < quantity) {
                return ResponseEntity.badRequest().body("Insufficient stock");
            }

            // Cập nhật số lượng
            Order updatedOrder = orderService.updateOrderItemQuantity(orderId, productId, quantity);

            // Cập nhật tổng tiền
            List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(orderId);
            BigDecimal totalAmount = orderItems.stream()
                    .map(OrderItem::getSubtotal)
                    .filter(java.util.Objects::nonNull) // 🔥
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            updatedOrder.setTotalAmount(totalAmount);
            orderService.saveOrder(updatedOrder);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Quantity updated successfully");
            response.put("orderId", orderId);
            response.put("totalAmount", totalAmount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error updating item quantity: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to update quantity: " + e.getMessage());
        }
    }

    /**
     * Xác nhận order (chuyển sang trạng thái CONFIRMED)
     */
    @PostMapping("/orders/{orderId}/confirm")
    public ResponseEntity<?> confirmOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        try {
            System.out.println("✅ Confirming order: " + orderId);

            // Kiểm tra order thuộc về user
            Order order = orderService.getOrderById(orderId);
            if (order == null || !order.getUser().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Order not found or access denied");
            }

            // Kiểm tra order có items không
            List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(orderId);
            if (orderItems.isEmpty()) {
                return ResponseEntity.badRequest().body("Order is empty");
            }

            // Cập nhật trạng thái
            order.setStatus(Order.OrderStatus.CONFIRMED);
            orderService.saveOrder(order);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Order confirmed successfully");
            response.put("orderId", orderId);
            response.put("status", Order.OrderStatus.CONFIRMED);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error confirming order: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to confirm order: " + e.getMessage());
        }
    }

    /**
     * Hủy order
     */
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        try {
            System.out.println("❌ Cancelling order: " + orderId);

            // Kiểm tra order thuộc về user
            Order order = orderService.getOrderById(orderId);
            if (order == null || !order.getUser().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Order not found or access denied");
            }

            // Cập nhật trạng thái
            order.setStatus(Order.OrderStatus.CANCELLED);
            orderService.saveOrder(order);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Order cancelled successfully");
            response.put("orderId", orderId);
            response.put("status", Order.OrderStatus.CANCELLED);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error cancelling order: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to cancel order: " + e.getMessage());
        }
    }

    /**
     * Xem tất cả orders của user
     */
    @GetMapping("/orders")
    public ResponseEntity<?> getUserOrders(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            System.out.println("📜 Getting orders for user: " + userDetails.getUsername());

            // Lấy tất cả orders của user
            List<Order> userOrders = orderService.getOrdersByUserId(userDetails.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userDetails.getId());
            response.put("username", userDetails.getUsername());
            response.put("orders", userOrders);
            response.put("totalOrders", userOrders.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error getting user orders: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to get orders");
        }
    }

    // === BILL & PAYMENT ===

    /**
     * Tạo bill từ order (thanh toán)
     */
    @PostMapping("/orders/{orderId}/pay")
    public ResponseEntity<?> payOrder(
            @PathVariable Long orderId,
            @RequestBody PaymentRequest paymentRequest,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Order order = orderService.getOrderById(orderId);

        if (order == null)
            return ResponseEntity.notFound().build();

        if (order.getStatus() != OrderStatus.PENDING) {
            return ResponseEntity.ok(Map.of(
                    "order", null,
                    "items", List.of()));
        }

        if ("PAYOS".equalsIgnoreCase(paymentRequest.getPaymentMethod())) {

            String checkoutUrl = payOSService.createPaymentLink(
                    order.getId(),
                    order.getTotalAmount());

            if (checkoutUrl == null) {
                return ResponseEntity.status(500).body("Failed to create payment link");
            }

            return ResponseEntity.ok(Map.of(
                    "paymentMethod", "PAYOS",
                    "checkoutUrl", checkoutUrl));
        }

        // CASH / MOMO
        Bill bill = billService.createBill(
                orderId,
                Bill.PaymentMethod.valueOf(paymentRequest.getPaymentMethod().toUpperCase()));

        order.setStatus(Order.OrderStatus.PAID);
        orderService.saveOrder(order);

        return ResponseEntity.ok(bill);
    }

    /**
     * Xem bill theo order
     */
    @GetMapping("/orders/{orderId}/bill")
    public ResponseEntity<?> getOrderBill(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        try {
            System.out.println("🧾 Getting bill for order: " + orderId);

            // Kiểm tra order thuộc về user
            Order order = orderService.getOrderById(orderId);
            if (order == null || !order.getUser().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body("Order not found or access denied");
            }

            // Lấy bill
            Bill bill = billService.getBillByOrderId(orderId);
            if (bill == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(bill);

        } catch (Exception e) {
            System.out.println("❌ Error getting bill: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to get bill");
        }
    }

    /**
     * Tính tổng tiền order
     */
    @GetMapping("/orders/{orderId}/calculate")
    public ResponseEntity<?> calculateOrderTotal(@PathVariable Long orderId) {
        try {
            System.out.println("🧮 Calculating total for order: " + orderId);

            Order order = orderService.getOrderById(orderId);
            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(orderId);

            // Tính tổng
            BigDecimal subtotal = orderItems.stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Áp dụng promotion nếu có
            BigDecimal discount = BigDecimal.ZERO;
            if (order.getPromotion() != null) {
                if (order.getPromotion().getDiscountPercentage() != null) {
                    discount = subtotal.multiply(order.getPromotion().getDiscountPercentage()
                            .divide(BigDecimal.valueOf(100)));
                } else if (order.getPromotion().getDiscountAmount() != null) {
                    discount = order.getPromotion().getDiscountAmount();
                }
            }

            BigDecimal total = subtotal.subtract(discount);

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("subtotal", subtotal);
            response.put("discount", discount);
            response.put("total", total);
            response.put("itemCount", orderItems.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ Error calculating total: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to calculate total");
        }

    }

    @PostMapping("/payos/webhook")
    public ResponseEntity<?> handlePayOSWebhook(@RequestBody Map<String, Object> payload) {

        log.info("🔔 PayOS webhook payload: {}", payload);

        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        Long orderCode = Long.valueOf(data.get("orderCode").toString());
        String status = data.get("status").toString();

        Bill bill = billRepository.findByPayosOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        if ("PAID".equalsIgnoreCase(status)) {

            bill.setPaymentStatus(Bill.PaymentStatus.COMPLETED);

            Order order = bill.getOrder();
            order.setStatus(Order.OrderStatus.PAID);

            billRepository.save(bill);
            orderRepository.save(order);

            log.info("✅ Order {} marked as PAID", order.getId());
        }

        return ResponseEntity.ok(Map.of("success", true));
    }

    // DTO for order item request (giữ nguyên)
    public static class OrderItemRequest {
        private Long productId;
        private Integer quantity;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

}