package com.tathang.example304.controllers;

import com.tathang.example304.model.Bill;
import com.tathang.example304.model.Order;
import com.tathang.example304.security.services.BillService;
import com.tathang.example304.security.services.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("/payment")
@Slf4j
public class PaymentController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private BillService billService;

    @GetMapping("/success")
    public RedirectView paymentSuccess(@RequestParam Long orderId) {
        try {
            log.info("✅ PayOS payment success callback - orderId: {}", orderId);

            Order order = orderService.getOrderById(orderId);

            if (order != null) {

                orderService.saveOrder(order);
            }

            return new RedirectView("myapp://payment/success?orderId=" + orderId);

        } catch (Exception e) {
            log.error("❌ Error handling payment success", e);
            return new RedirectView("myapp://payment/error");
        }
    }

    @GetMapping("/cancel")
    public RedirectView paymentCancel(@RequestParam Long orderId) {

        Bill bill = billService.getPendingPayosBillByOrderId(orderId);

        if (bill != null) {
            bill.setPaymentStatus(Bill.PaymentStatus.FAILED);
            billService.save(bill);
            log.info("❌ PayOS bill {} marked FAILED", bill.getId());
        }

        return new RedirectView("myapp://payment/cancel?orderId=" + orderId);
    }
}