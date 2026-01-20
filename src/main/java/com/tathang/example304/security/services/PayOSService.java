package com.tathang.example304.security.services;

import com.tathang.example304.model.Bill;
import com.tathang.example304.model.Order;
import com.tathang.example304.model.OrderItem;
import com.tathang.example304.repository.BillRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class PayOSService {

    @Autowired
    private OrderService orderService;

    @Autowired
    private BillRepository billRepository;

    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    @Value("${app.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String PAYOS_API_URL = "https://api-merchant.payos.vn/v2/payment-requests";

    public String createPaymentLink(Long orderId, BigDecimal totalAmount) {

        Order order = orderService.getOrderById(orderId);
        if (order == null) {
            throw new RuntimeException("Order not found");
        }

        // ✅ XÓA BILL PAYOS CŨ (nếu user hủy thanh toán)
        billRepository.deleteByOrderIdAndPaymentMethod(
                orderId,
                Bill.PaymentMethod.PAYOS);

        long orderCode = System.currentTimeMillis();
        int amount = totalAmount.intValue();

        String description = "Thanh toan don hang #" + orderId;
        String returnUrl = baseUrl + "/payment/success?orderId=" + orderId;
        String cancelUrl = baseUrl + "/payment/cancel?orderId=" + orderId;

        String signature = createSignature(
                orderCode, amount, description, returnUrl, cancelUrl);

        List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(orderId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderItem item : orderItems) {
            items.add(Map.of(
                    "name", item.getProduct().getName(),
                    "quantity", item.getQuantity(),
                    "price", item.getPrice().intValue()));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", description);
        body.put("returnUrl", returnUrl);
        body.put("cancelUrl", cancelUrl);
        body.put("signature", signature);
        body.put("items", items);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);

        ResponseEntity<Map> response = restTemplate.exchange(
                PAYOS_API_URL,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

        String checkoutUrl = data.get("checkoutUrl").toString();

        // ✅ CHỈ LƯU BILL – KHÔNG ĐỔI ORDER STATUS
        Bill bill = new Bill();
        bill.setOrder(order);
        bill.setPaymentMethod(Bill.PaymentMethod.PAYOS);
        bill.setPaymentStatus(Bill.PaymentStatus.PENDING);
        bill.setPayosOrderCode(orderCode);
        bill.setCheckoutUrl(checkoutUrl);

        billRepository.save(bill);

        log.info("✅ PAYOS link created for order {}", orderId);

        return checkoutUrl;
    }

    private String createSignature(
            long orderCode,
            int amount,
            String description,
            String returnUrl,
            String cancelUrl) {

        try {
            String raw = "amount=" + amount +
                    "&cancelUrl=" + cancelUrl +
                    "&description=" + description +
                    "&orderCode=" + orderCode +
                    "&returnUrl=" + returnUrl;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    checksumKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));

            byte[] hash = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}