package com.ecommerce.inventory.dto;

public class ReserveRequest {
    private String productId;
    private int    quantity;
    private String orderId;

    public String getProductId()               { return productId; }
    public void   setProductId(String id)      { this.productId = id; }
    public int    getQuantity()                { return quantity; }
    public void   setQuantity(int qty)         { this.quantity = qty; }
    public String getOrderId()                 { return orderId; }
    public void   setOrderId(String orderId)   { this.orderId = orderId; }
}
