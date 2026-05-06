package com.ecommerce.order.client;

import com.ecommerce.order.dto.PaymentRequest;
import com.ecommerce.order.dto.PaymentResponse;
import com.ecommerce.order.exception.PaymentServiceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Client HTTP vers l'API de paiement externe.
 *
 * Protégé par un Circuit Breaker Resilience4j :
 *   - CLOSED  → appels normaux
 *   - OPEN    → fast-fail, fallback immédiat
 *   - HALF-OPEN → 2 appels tests puis décision
 */
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestTemplate restTemplate;

    @Value("${payment.service.url:http://localhost:9090}")
    private String paymentUrl;

    public PaymentClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ─── Appel principal protégé par Circuit Breaker ─────────────────────────
    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackProcessPayment")
    public PaymentResponse processPayment(PaymentRequest request) {
        String url = paymentUrl + "/api/payments";
        log.info("[CB] → Appel PaymentAPI | orderId={} | montant={}",
                request.getOrderId(), request.getAmount());

        try {
            ResponseEntity<PaymentResponse> resp =
                    restTemplate.postForEntity(url, request, PaymentResponse.class);

            if (resp.getBody() == null) {
                throw new PaymentServiceException("Réponse vide de PaymentAPI");
            }

            log.info("[CB] ← PaymentAPI OK | status={}", resp.getBody().getStatus());
            return resp.getBody();

        } catch (RestClientException e) {
            log.error("[CB] ✗ PaymentAPI inaccessible : {}", e.getMessage());
            throw new PaymentServiceException("PaymentAPI indisponible", e);
        }
    }

    // ─── Fallback : circuit OPEN ou erreur ───────────────────────────────────
    public PaymentResponse fallbackProcessPayment(PaymentRequest request, Throwable t) {
        String reason = (t instanceof CallNotPermittedException)
                ? "Circuit OUVERT — fast-fail"
                : "Erreur service : " + t.getMessage();

        log.warn("[CB] ⚡ FALLBACK déclenché | orderId={} | raison={}",
                request.getOrderId(), reason);

        PaymentResponse fallback = new PaymentResponse();
        fallback.setTransactionId("FALLBACK-" + UUID.randomUUID().toString().substring(0, 8));
        fallback.setStatus("PENDING");
        fallback.setMessage("Paiement différé — " + reason);
        fallback.setAmount(request.getAmount());
        fallback.setFallback(true);
        return fallback;
    }
}
