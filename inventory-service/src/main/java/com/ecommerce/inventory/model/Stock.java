package com.ecommerce.inventory.model;

import java.time.LocalDateTime;

public class Stock {

    private String        productId;
    private String        productName;
    private int           available;
    private int           reserved;
    private double        unitPrice;
    private LocalDateTime lastUpdated;

    public Stock() {}

    public Stock(String productId, String productName, int available, double unitPrice) {
        this.productId   = productId;
        this.productName = productName;
        this.available   = available;
        this.reserved    = 0;
        this.unitPrice   = unitPrice;
        this.lastUpdated = LocalDateTime.now();
    }

    // ─── Business logic ──────────────────────────────────────────────────────

    public synchronized boolean reserve(int qty) {
        if (this.available >= qty) {
            this.available -= qty;
            this.reserved  += qty;
            this.lastUpdated = LocalDateTime.now();
            return true;
        }
        return false;
    }

    public synchronized void release(int qty) {
        int actual = Math.min(qty, this.reserved);
        this.reserved  -= actual;
        this.available += actual;
        this.lastUpdated = LocalDateTime.now();
    }

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public String        getProductId()                    { return productId; }
    public void          setProductId(String id)           { this.productId = id; }
    public String        getProductName()                  { return productName; }
    public void          setProductName(String name)       { this.productName = name; }
    public int           getAvailable()                    { return available; }
    public void          setAvailable(int qty)             { this.available = qty; }
    public int           getReserved()                     { return reserved; }
    public void          setReserved(int qty)              { this.reserved = qty; }
    public double        getUnitPrice()                    { return unitPrice; }
    public void          setUnitPrice(double price)        { this.unitPrice = price; }
    public LocalDateTime getLastUpdated()                  { return lastUpdated; }
    public void          setLastUpdated(LocalDateTime dt)  { this.lastUpdated = dt; }
}
