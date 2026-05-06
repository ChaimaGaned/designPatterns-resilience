package com.ecommerce.order.service;

import com.ecommerce.order.client.PaymentClient;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.PaymentRequest;
import com.ecommerce.order.dto.PaymentResponse;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // In-memory store (pas de DB pour simplifier)
    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();

    private final PaymentClient paymentClient;

    @Value("${server.port:8081}")
    private String serverPort;

    public OrderService(PaymentClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    // ─── Créer une commande ──────────────────────────────────────────────────
    public OrderResponse createOrder(CreateOrderRequest req) {
        // 1. Calcul du montant total
        double total = req.getItems().stream()
                .mapToDouble(OrderItem::getSubtotal)
                .sum();

        // 2. Créer l'entité Order
        Order order = new Order(
                "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                req.getCustomerId(),
                req.getItems(),
                total
        );

        // 3. Persister (in-memory)
        orders.put(order.getOrderId(), order);
        log.info("[ORDER] Commande créée | id={} | total={}", order.getOrderId(), total);

        // 4. Appel PaymentAPI via Circuit Breaker
        PaymentRequest paymentReq = new PaymentRequest(
                order.getOrderId(), order.getCustomerId(), total
        );
        PaymentResponse paymentResp = paymentClient.processPayment(paymentReq);

        // 5. Mettre à jour le statut selon la réponse paiement
        if ("SUCCESS".equals(paymentResp.getStatus())) {
            order.setStatus(Order.OrderStatus.CONFIRMED);
        } else if ("PENDING".equals(paymentResp.getStatus())) {
            order.setStatus(Order.OrderStatus.PAYMENT_PENDING);
        } else {
            order.setStatus(Order.OrderStatus.PAYMENT_FAILED);
        }

        return OrderResponse.of(order, paymentResp, "order-service:" + serverPort);
    }

    // ─── Récupérer une commande ──────────────────────────────────────────────
    public Order getOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new RuntimeException("Commande non trouvée : " + orderId);
        return order;
    }

    // ─── Liste toutes les commandes ──────────────────────────────────────────
    public List<Order> getAllOrders() {
        return List.copyOf(orders.values());
    }
}
