package com.cme;

public interface OrderRequestService {
    public void submit(Order order);
    public void cancel(int orderId);
}
