package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.service.OrderService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService            orderService;
    private final CircuitBreakerRegistry  cbRegistry;

    @Value("${server.port:8081}")
    private String serverPort;

    public OrderController(OrderService orderService, CircuitBreakerRegistry cbRegistry) {
        this.orderService = orderService;
        this.cbRegistry   = cbRegistry;
    }

    // ─── Health ping ──────────────────────────────────────────────────────────
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        Map<String, String> resp = new HashMap<>();
        resp.put("service",  "order-service");
        resp.put("port",     serverPort);
        resp.put("status",   "UP");
        resp.put("circuit",  getCircuitState());
        return ResponseEntity.ok(resp);
    }

    // ─── POST /api/orders — Créer une commande ────────────────────────────────
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest req) {
        log.info("[CTRL] POST /api/orders | customer={}", req.getCustomerId());
        OrderResponse resp = orderService.createOrder(req);
        return ResponseEntity.ok(resp);
    }

    // ─── GET /api/orders/{id} ─────────────────────────────────────────────────
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    // ─── GET /api/orders ──────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    // ─── GET /api/orders/circuit-status — État du Circuit Breaker ────────────
    @GetMapping("/circuit-status")
    public ResponseEntity<Map<String, Object>> circuitStatus() {
        CircuitBreaker cb = cbRegistry.circuitBreaker("paymentService");
        CircuitBreaker.Metrics m = cb.getMetrics();

        Map<String, Object> status = new HashMap<>();
        status.put("name",              "paymentService");
        status.put("state",             cb.getState().toString());
        status.put("failureRate",       m.getFailureRate() + "%");
        status.put("slowCallRate",      m.getSlowCallRate() + "%");
        status.put("successCalls",      m.getNumberOfSuccessfulCalls());
        status.put("failedCalls",       m.getNumberOfFailedCalls());
        status.put("notPermittedCalls", m.getNumberOfNotPermittedCalls());
        status.put("bufferedCalls",     m.getNumberOfBufferedCalls());
        status.put("serviceInstance",   "order-service:" + serverPort);
        return ResponseEntity.ok(status);
    }

    // ─── POST /api/orders/simulate-failure — Force une erreur (test) ─────────
    @PostMapping("/simulate-failure")
    public ResponseEntity<Map<String, String>> simulateFailure() {
        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Endpoint de test : utilisez PaymentAPI /api/payments/fail");
        resp.put("tip",     "Démarrez payment-mock avec FAIL_RATE=100 pour forcer les échecs");
        return ResponseEntity.ok(resp);
    }

    private String getCircuitState() {
        try {
            return cbRegistry.circuitBreaker("paymentService").getState().toString();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
