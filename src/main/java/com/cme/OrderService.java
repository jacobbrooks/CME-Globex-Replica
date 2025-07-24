package com.cme;

public interface OrderService {
    public void submit(Order order);
    public void cancel(int orderId);
}
