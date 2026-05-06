package com.ecommerce.payment.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API de paiement simulée.
 *
 * Contrôlable via variables d'environnement :
 *   FAIL_RATE    : 0-100, pourcentage d'échecs simulés (défaut: 0)
 *   LATENCY_MS   : latence artificielle en ms           (défaut: 100)
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger     log     = LoggerFactory.getLogger(PaymentController.class);
    private static final Random     rng     = new Random();
    private final        AtomicInteger callCount = new AtomicInteger(0);

    @Value("${payment.fail-rate:0}")
    private int failRate;

    @Value("${payment.latency-ms:100}")
    private int latencyMs;

    // ─── POST /api/payments — Traiter un paiement ─────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, Object>> processPayment(
            @RequestBody Map<String, Object> request) throws InterruptedException {

        int call = callCount.incrementAndGet();
        String orderId = (String) request.getOrDefault("orderId", "UNKNOWN");
        log.info("[MOCK] Appel #{} | orderId={} | failRate={}%", call, orderId, failRate);

        // Latence simulée
        Thread.sleep(latencyMs);

        // Décision succès/échec selon FAIL_RATE
        boolean fail = rng.nextInt(100) < failRate;

        Map<String, Object> resp = new HashMap<>();
        resp.put("orderId", orderId);
        resp.put("callNumber", call);

        if (fail) {
            log.warn("[MOCK] ✗ Échec simulé #{} | orderId={}", call, orderId);
            resp.put("transactionId", null);
            resp.put("status",  "FAILED");
            resp.put("message", "Paiement refusé — erreur simulée (failRate=" + failRate + "%)");
            return ResponseEntity.status(503).body(resp);
        }

        String txId = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        log.info("[MOCK] ✓ Succès #{} | txId={}", call, txId);
        resp.put("transactionId", txId);
        resp.put("status",  "SUCCESS");
        resp.put("message", "Paiement accepté");
        resp.put("amount",  request.get("amount"));
        return ResponseEntity.ok(resp);
    }

    // ─── GET /api/payments/ping ───────────────────────────────────────────
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("service",   "payment-mock");
        resp.put("failRate",  failRate + "%");
        resp.put("latencyMs", latencyMs);
        resp.put("totalCalls", callCount.get());
        resp.put("status", "UP");
        return ResponseEntity.ok(resp);
    }

    // ─── POST /api/payments/fail — Force toujours un échec (test CB) ──────
    @PostMapping("/fail")
    public ResponseEntity<Map<String, Object>> forceFailure() {
        log.warn("[MOCK] /fail endpoint appelé — retour 503");
        Map<String, Object> resp = new HashMap<>();
        resp.put("status",  "FAILED");
        resp.put("message", "Échec forcé pour test Circuit Breaker");
        return ResponseEntity.status(503).body(resp);
    }

    // ─── PUT /api/payments/config — Changer fail-rate à chaud ────────────
    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @RequestParam(defaultValue = "0")  int failRate,
            @RequestParam(defaultValue = "100") int latencyMs) {
        this.failRate  = failRate;
        this.latencyMs = latencyMs;
        log.info("[MOCK] Config mise à jour : failRate={}% latency={}ms", failRate, latencyMs);
        Map<String, Object> resp = new HashMap<>();
        resp.put("failRate",  this.failRate + "%");
        resp.put("latencyMs", this.latencyMs);
        resp.put("message",   "Configuration mise à jour");
        return ResponseEntity.ok(resp);
    }
}
