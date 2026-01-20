package com.tathang.example304.security.services;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import com.tathang.example304.model.Bill;
import com.tathang.example304.model.Order;
import com.tathang.example304.repository.BillRepository;
import com.tathang.example304.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class BillService {

    private final BillRepository billRepository;
    private final OrderRepository orderRepository;

    public BillService(BillRepository billRepository, OrderRepository orderRepository) {
        this.billRepository = billRepository;
        this.orderRepository = orderRepository;
    }

    // ✅ CASH / MOMO
    public Bill createBill(Long orderId, Bill.PaymentMethod paymentMethod) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        billRepository.findByOrder_Id(orderId)
                .ifPresent(b -> {
                    throw new RuntimeException("Order đã có bill");
                });

        Bill bill = new Bill(order, order.getTotalAmount());
        bill.setPaymentMethod(paymentMethod);
        bill.setIssuedAt(LocalDateTime.now());

        // 🔥 PHÂN BIỆT RÕ
        if (paymentMethod == Bill.PaymentMethod.PAYOS) {
            bill.setPaymentStatus(Bill.PaymentStatus.PENDING); // ⏳ chờ webhook
        } else {
            bill.setPaymentStatus(Bill.PaymentStatus.COMPLETED); // CASH / MOMO
            order.setStatus(Order.OrderStatus.PAID);
            orderRepository.save(order);
        }

        return billRepository.save(bill);
    }

    // ✅ PAYOS – CHẶN TRÙNG
    public Bill getPendingPayosBillByOrderId(Long orderId) {
        return billRepository
                .findByOrderIdAndPaymentMethodAndPaymentStatus(
                        orderId,
                        Bill.PaymentMethod.PAYOS,
                        Bill.PaymentStatus.PENDING)
                .orElse(null);
    }

    public Bill save(Bill bill) {
        return billRepository.save(bill);
    }

    public Bill getBillByOrderId(Long orderId) {
        return billRepository.findByOrder_Id(orderId).orElse(null);
    }
}