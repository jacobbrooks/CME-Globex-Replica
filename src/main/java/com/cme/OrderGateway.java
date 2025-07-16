package com.cme;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

public class OrderGateway implements OrderScheduler {

    final PriorityBlockingQueue<Order> orderQueue = new PriorityBlockingQueue<>(1, Comparator.comparing(Order::getTimestamp));
    final Map<Integer, OrderBook> orderBooks = new ConcurrentHashMap<>();
    final Map<Integer, Security> securitiesById = new HashMap<>();

    public void addOrderBook(OrderBook orderbook) {
        orderBooks.put(orderbook.getSecurity().getId(), orderbook);
    }

    public void start() {
        Runnable queueConsumer = () -> {
            while (true) {
                final Order nextOrder = orderQueue.poll();
                if (nextOrder == null) {
                    continue;
                }
                final OrderBook book = orderBooks.computeIfAbsent(nextOrder.getSecurity().getId(), k -> new OrderBook(securitiesById.get(nextOrder.getSecurity().getId()), this));
                book.addOrder(nextOrder);
            }
        };
        new Thread(queueConsumer).start();
    }

    @Override
    public void submit(Order order) {
        orderQueue.put(order);
    }

    public void printBook(int securityId) {
        System.out.println(orderBooks.get(securityId).toString());
    }

}
