package com.ecommerce.order.model;

import java.time.LocalDateTime;
import java.util.List;

public class Order {

    private String orderId;
    private String customerId;
    private List<OrderItem> items;
    private double totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;

    public enum OrderStatus {
        PENDING, CONFIRMED, PAYMENT_FAILED, PAYMENT_PENDING
    }

    public Order() {}

    public Order(String orderId, String customerId, List<OrderItem> items, double totalAmount) {
        this.orderId     = orderId;
        this.customerId  = customerId;
        this.items       = items;
        this.totalAmount = totalAmount;
        this.status      = OrderStatus.PENDING;
        this.createdAt   = LocalDateTime.now();
    }

    // ─── Getters & Setters ────────────────────────────────────────────

    public String getOrderId()                    { return orderId; }
    public void   setOrderId(String orderId)      { this.orderId = orderId; }

    public String getCustomerId()                  { return customerId; }
    public void   setCustomerId(String id)         { this.customerId = id; }

    public List<OrderItem> getItems()              { return items; }
    public void   setItems(List<OrderItem> items)  { this.items = items; }

    public double getTotalAmount()                 { return totalAmount; }
    public void   setTotalAmount(double amount)    { this.totalAmount = amount; }

    public OrderStatus getStatus()                 { return status; }
    public void   setStatus(OrderStatus status)    { this.status = status; }

    public LocalDateTime getCreatedAt()            { return createdAt; }
    public void   setCreatedAt(LocalDateTime dt)   { this.createdAt = dt; }
}
