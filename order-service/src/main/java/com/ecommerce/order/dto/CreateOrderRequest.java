package com.ecommerce.order.dto;

import com.ecommerce.order.model.OrderItem;
import java.util.List;

public class CreateOrderRequest {
    private String customerId;
    private List<OrderItem> items;

    public String getCustomerId()                  { return customerId; }
    public void   setCustomerId(String id)         { this.customerId = id; }
    public List<OrderItem> getItems()              { return items; }
    public void   setItems(List<OrderItem> items)  { this.items = items; }
}
