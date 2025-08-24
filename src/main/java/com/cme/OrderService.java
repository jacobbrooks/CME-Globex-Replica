package com.cme;

public interface OrderService {
    public void add(Order order);
    public void cancel(OrderCancel orderCancel);
    public void modify(OrderModify orderModify);
}
