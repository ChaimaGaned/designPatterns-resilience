package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.ReserveRequest;
import com.ecommerce.inventory.dto.StockResponse;
import com.ecommerce.inventory.service.InventoryService;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;
    private final BulkheadRegistry bulkheadRegistry;

    @Value("${server.port:8082}")
    private String serverPort;

    public InventoryController(InventoryService inventoryService,
                               BulkheadRegistry bulkheadRegistry) {
        this.inventoryService = inventoryService;
        this.bulkheadRegistry = bulkheadRegistry;
    }

    // ─── Health ping ──────────────────────────────────────────────────────────
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("service",  "inventory-service");
        resp.put("port",     serverPort);
        resp.put("status",   "UP");
        resp.put("bulkheads", getBulkheadStatus());
        return ResponseEntity.ok(resp);
    }

    // ─── GET /api/inventory — Tout le catalogue ────────────────────────────
    @GetMapping
    public ResponseEntity<List<StockResponse>> getAllStock() {
        return ResponseEntity.ok(inventoryService.getAllStock());
    }

    // ─── GET /api/inventory/{productId} — Stock d'un produit ──────────────
    @GetMapping("/{productId}")
    public ResponseEntity<StockResponse> getStock(@PathVariable String productId)
            throws InterruptedException {
        log.info("[CTRL] GET /api/inventory/{}", productId);
        StockResponse resp = inventoryService.getStock(productId);
        if (resp.isFallback()) {
            return ResponseEntity.status(429).body(resp);
        }
        return ResponseEntity.ok(resp);
    }

    // ─── POST /api/inventory/reserve — Réserver du stock ─────────────────
    @PostMapping("/reserve")
    public ResponseEntity<StockResponse> reserveStock(@RequestBody ReserveRequest req)
            throws InterruptedException {
        log.info("[CTRL] POST /api/inventory/reserve | product={} qty={}",
                req.getProductId(), req.getQuantity());
        StockResponse resp = inventoryService.reserveStock(req);
        if (resp.isFallback()) {
            return ResponseEntity.status(429).body(resp);
        }
        return ResponseEntity.ok(resp);
    }

    // ─── GET /api/inventory/bulkhead-status ───────────────────────────────
    @GetMapping("/bulkhead-status")
    public ResponseEntity<Map<String, Object>> bulkheadStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("serviceInstance", "inventory-service:" + serverPort);
        result.put("bulkheads", getBulkheadStatus());
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> getBulkheadStatus() {
        Map<String, Object> bhs = new HashMap<>();
        for (String name : List.of("readStock", "reserveStock")) {
            try {
                Bulkhead bh = bulkheadRegistry.bulkhead(name);
                Bulkhead.Metrics m = bh.getMetrics();
                Map<String, Object> info = new HashMap<>();
                info.put("maxConcurrent",       bh.getBulkheadConfig().getMaxConcurrentCalls());
                info.put("availablePermissions", m.getAvailableConcurrentCalls());
                bhs.put(name, info);
            } catch (Exception e) {
                bhs.put(name, "non configuré");
            }
        }
        return bhs;
    }
}
