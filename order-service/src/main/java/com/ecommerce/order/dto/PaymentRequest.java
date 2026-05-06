package com.ecommerce.order.dto;

public class PaymentRequest {
    private String orderId;
    private String customerId;
    private double amount;
    private String currency;

    public PaymentRequest() {}

    public PaymentRequest(String orderId, String customerId, double amount) {
        this.orderId    = orderId;
        this.customerId = customerId;
        this.amount     = amount;
        this.currency   = "TND";
    }

    public String getOrderId()             { return orderId; }
    public void   setOrderId(String id)    { this.orderId = id; }
    public String getCustomerId()          { return customerId; }
    public void   setCustomerId(String id) { this.customerId = id; }
    public double getAmount()              { return amount; }
    public void   setAmount(double a)      { this.amount = a; }
    public String getCurrency()            { return currency; }
    public void   setCurrency(String c)    { this.currency = c; }
}
