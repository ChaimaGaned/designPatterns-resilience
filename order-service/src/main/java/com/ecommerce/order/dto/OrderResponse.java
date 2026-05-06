package com.ecommerce.order.dto;

import com.ecommerce.order.model.Order;
import java.time.LocalDateTime;

public class OrderResponse {
    private String            orderId;
    private String            customerId;
    private double            totalAmount;
    private Order.OrderStatus orderStatus;
    private String            paymentStatus;
    private String            paymentMessage;
    private boolean           paymentFallback;
    private LocalDateTime     createdAt;
    private String            serviceInstance;

    public static OrderResponse of(Order order, PaymentResponse payment, String instance) {
        OrderResponse r     = new OrderResponse();
        r.orderId           = order.getOrderId();
        r.customerId        = order.getCustomerId();
        r.totalAmount       = order.getTotalAmount();
        r.orderStatus       = order.getStatus();
        r.paymentStatus     = payment.getStatus();
        r.paymentMessage    = payment.getMessage();
        r.paymentFallback   = payment.isFallback();
        r.createdAt         = order.getCreatedAt();
        r.serviceInstance   = instance;
        return r;
    }

    public String            getOrderId()                       { return orderId; }
    public void              setOrderId(String id)              { this.orderId = id; }
    public String            getCustomerId()                    { return customerId; }
    public void              setCustomerId(String id)           { this.customerId = id; }
    public double            getTotalAmount()                   { return totalAmount; }
    public void              setTotalAmount(double a)           { this.totalAmount = a; }
    public Order.OrderStatus getOrderStatus()                   { return orderStatus; }
    public void              setOrderStatus(Order.OrderStatus s){ this.orderStatus = s; }
    public String            getPaymentStatus()                 { return paymentStatus; }
    public void              setPaymentStatus(String s)         { this.paymentStatus = s; }
    public String            getPaymentMessage()                { return paymentMessage; }
    public void              setPaymentMessage(String m)        { this.paymentMessage = m; }
    public boolean           isPaymentFallback()                { return paymentFallback; }
    public void              setPaymentFallback(boolean f)      { this.paymentFallback = f; }
    public LocalDateTime     getCreatedAt()                     { return createdAt; }
    public void              setCreatedAt(LocalDateTime dt)     { this.createdAt = dt; }
    public String            getServiceInstance()               { return serviceInstance; }
    public void              setServiceInstance(String s)       { this.serviceInstance = s; }
}
