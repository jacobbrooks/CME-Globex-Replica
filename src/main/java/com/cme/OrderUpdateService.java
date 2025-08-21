package com.cme;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OrderUpdateService {
    private final Map<Integer, Queue<OrderUpdate>> orderUpdateMap = new ConcurrentHashMap<>();

    public void pushOrderUpdate(int orderId, OrderUpdate update) {
        orderUpdateMap.computeIfAbsent(orderId, k -> new ConcurrentLinkedQueue<>()).add(update);
    }

    public List<OrderUpdate> getOrderUpdates(int orderId) {
        return orderUpdateMap.get(orderId).stream().toList();
    }

    public void clear() {
        orderUpdateMap.clear();
    }
}
