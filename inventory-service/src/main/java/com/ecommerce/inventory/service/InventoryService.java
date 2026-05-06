package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.ReserveRequest;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.model.Stock;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion du stock.
 *
 * Deux Bulkheads distincts :
 *   - readStock    : lectures — forte concurrence (max=10)
 *   - reserveStock : écritures — concurrence restreinte (max=5)
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    @Value("${server.port:8082}")
    private String serverPort;

    // ─── Catalogue de produits (in-memory) ───────────────────────────────────
    private final Map<String, Stock> catalog = new ConcurrentHashMap<>();

    public InventoryService() {
        // Données initiales
        catalog.put("SKU-LAPTOP",  new Stock("SKU-LAPTOP",  "Laptop Dell XPS 15",   50,  2499.99));
        catalog.put("SKU-PHONE",   new Stock("SKU-PHONE",   "Samsung Galaxy S24",    120, 1199.99));
        catalog.put("SKU-TABLET",  new Stock("SKU-TABLET",  "iPad Air 5",            75,  899.99));
        catalog.put("SKU-WATCH",   new Stock("SKU-WATCH",   "Apple Watch Series 9",  200, 499.99));
        catalog.put("SKU-HEADSET", new Stock("SKU-HEADSET", "Sony WH-1000XM5",       300, 349.99));
    }

    // ─── Bulkhead LECTURE — max 10 appels simultanés ─────────────────────────
    @Bulkhead(name = "readStock", fallbackMethod = "fallbackGetStock",
              type = Bulkhead.Type.SEMAPHORE)
    public StockResponse getStock(String productId) throws InterruptedException {
        log.debug("[BH-READ] Lecture stock | productId={} | thread={}", 
                productId, Thread.currentThread().getName());

        // Simule une latence DB légère
        Thread.sleep(30);

        Stock stock = catalog.get(productId);
        if (stock == null) {
            throw new RuntimeException("Produit introuvable : " + productId);
        }
        return StockResponse.from(stock, instance());
    }

    // ─── Fallback lecture ────────────────────────────────────────────────────
    public StockResponse fallbackGetStock(String productId, BulkheadFullException e) {
        log.warn("[BH-READ] ⚡ Bulkhead saturé | productId={} | concurrent limit atteinte", productId);
        return StockResponse.unavailable(productId,
                "Service de lecture saturé — réessayez dans 1 seconde", instance());
    }

    public StockResponse fallbackGetStock(String productId, Throwable t) {
        log.warn("[BH-READ] ⚡ Fallback générique | productId={} | cause={}", productId, t.getMessage());
        return StockResponse.unavailable(productId,
                "Service temporairement indisponible", instance());
    }

    // ─── Bulkhead ÉCRITURE — max 5 appels simultanés ─────────────────────────
    @Bulkhead(name = "reserveStock", fallbackMethod = "fallbackReserveStock",
              type = Bulkhead.Type.SEMAPHORE)
    public StockResponse reserveStock(ReserveRequest req) throws InterruptedException {
        log.info("[BH-WRITE] Réservation | productId={} | qty={} | orderId={}",
                req.getProductId(), req.getQuantity(), req.getOrderId());

        // Simule une transaction assez longue pour rendre la saturation visible en demo.
        Thread.sleep(500);

        Stock stock = catalog.get(req.getProductId());
        if (stock == null) {
            throw new RuntimeException("Produit introuvable : " + req.getProductId());
        }

        boolean ok = stock.reserve(req.getQuantity());
        if (!ok) {
            StockResponse resp = StockResponse.from(stock, instance());
            resp.setMessage("Stock insuffisant : disponible=" + stock.getAvailable()
                    + ", demandé=" + req.getQuantity());
            return resp;
        }

        log.info("[BH-WRITE] ✓ Réservé {} x {} | restant={}",
                req.getQuantity(), req.getProductId(), stock.getAvailable());
        return StockResponse.from(stock, instance());
    }

    // ─── Fallback réservation ────────────────────────────────────────────────
    public StockResponse fallbackReserveStock(ReserveRequest req, BulkheadFullException e) {
        log.error("[BH-WRITE] ⚡ Bulkhead écriture saturé | productId={} | orderId={}",
                req.getProductId(), req.getOrderId());
        return StockResponse.unavailable(req.getProductId(),
                "Service de réservation saturé (max 5 simultanés) — HTTP 429", instance());
    }

    public StockResponse fallbackReserveStock(ReserveRequest req, Throwable t) {
        log.error("[BH-WRITE] ⚡ Fallback réservation | cause={}", t.getMessage());
        return StockResponse.unavailable(req.getProductId(),
                "Erreur réservation : " + t.getMessage(), instance());
    }

    // ─── Catalogue complet ───────────────────────────────────────────────────
    public List<StockResponse> getAllStock() {
        return catalog.values().stream()
                .map(s -> StockResponse.from(s, instance()))
                .toList();
    }

    private String instance() {
        return "inventory-service:" + serverPort;
    }
}
