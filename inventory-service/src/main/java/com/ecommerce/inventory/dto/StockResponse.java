package com.ecommerce.inventory.dto;

import com.ecommerce.inventory.model.Stock;

public class StockResponse {
    private String  productId;
    private String  productName;
    private int     available;
    private int     reserved;
    private double  unitPrice;
    private boolean fallback;
    private String  message;
    private String  serviceInstance;

    public static StockResponse from(Stock stock, String instance) {
        StockResponse r  = new StockResponse();
        r.productId      = stock.getProductId();
        r.productName    = stock.getProductName();
        r.available      = stock.getAvailable();
        r.reserved       = stock.getReserved();
        r.unitPrice      = stock.getUnitPrice();
        r.fallback       = false;
        r.serviceInstance = instance;
        return r;
    }

    public static StockResponse unavailable(String productId, String msg, String instance) {
        StockResponse r  = new StockResponse();
        r.productId      = productId;
        r.available      = -1;
        r.fallback       = true;
        r.message        = msg;
        r.serviceInstance = instance;
        return r;
    }

    public String  getProductId()                  { return productId; }
    public void    setProductId(String id)         { this.productId = id; }
    public String  getProductName()                { return productName; }
    public void    setProductName(String name)     { this.productName = name; }
    public int     getAvailable()                  { return available; }
    public void    setAvailable(int qty)           { this.available = qty; }
    public int     getReserved()                   { return reserved; }
    public void    setReserved(int qty)            { this.reserved = qty; }
    public double  getUnitPrice()                  { return unitPrice; }
    public void    setUnitPrice(double price)      { this.unitPrice = price; }
    public boolean isFallback()                    { return fallback; }
    public void    setFallback(boolean f)          { this.fallback = f; }
    public String  getMessage()                    { return message; }
    public void    setMessage(String msg)          { this.message = msg; }
    public String  getServiceInstance()            { return serviceInstance; }
    public void    setServiceInstance(String s)    { this.serviceInstance = s; }
}
