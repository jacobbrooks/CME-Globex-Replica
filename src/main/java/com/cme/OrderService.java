package com.cme;

public interface OrderService {
    public void submit(Order order);
    public void cancel(OrderCancel orderCancel);
    public void modify(OrderModify orderModify);
}
