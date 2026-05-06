package com.ecommerce.order.model;

public class OrderItem {

    private String productId;
    private String productName;
    private int    quantity;
    private double unitPrice;

    public OrderItem() {}

    public OrderItem(String productId, String productName, int quantity, double unitPrice) {
        this.productId   = productId;
        this.productName = productName;
        this.quantity    = quantity;
        this.unitPrice   = unitPrice;
    }

    public double getSubtotal() { return quantity * unitPrice; }

    public String getProductId()                  { return productId; }
    public void   setProductId(String productId)  { this.productId = productId; }

    public String getProductName()                { return productName; }
    public void   setProductName(String name)     { this.productName = name; }

    public int    getQuantity()                   { return quantity; }
    public void   setQuantity(int quantity)       { this.quantity = quantity; }

    public double getUnitPrice()                  { return unitPrice; }
    public void   setUnitPrice(double price)      { this.unitPrice = price; }
}
