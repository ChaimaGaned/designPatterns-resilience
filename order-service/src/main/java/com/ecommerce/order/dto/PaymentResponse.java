package com.ecommerce.order.dto;

public class PaymentResponse {
    private String  transactionId;
    private String  status;
    private String  message;
    private double  amount;
    private boolean fallback;

    public PaymentResponse() {}

    public String  getTransactionId()              { return transactionId; }
    public void    setTransactionId(String id)     { this.transactionId = id; }
    public String  getStatus()                     { return status; }
    public void    setStatus(String s)             { this.status = s; }
    public String  getMessage()                    { return message; }
    public void    setMessage(String m)            { this.message = m; }
    public double  getAmount()                     { return amount; }
    public void    setAmount(double a)             { this.amount = a; }
    public boolean isFallback()                    { return fallback; }
    public void    setFallback(boolean f)          { this.fallback = f; }
}
