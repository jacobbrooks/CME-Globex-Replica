package com.cme;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class TradingEngine implements OrderRequestService {

    final PriorityBlockingQueue<Order> ordersToAdd = new PriorityBlockingQueue<>(1, Comparator.comparing(Order::getTimestamp));
    final ConcurrentLinkedQueue<Integer> ordersToCancel = new ConcurrentLinkedQueue<>();

    final Map<Integer, OrderBook> orderBooksBySecurityId = new ConcurrentHashMap<>();
    final Map<Integer, OrderBook> orderBooksByOrderId = new ConcurrentHashMap<>();

    final Map<Integer, Security> securitiesById = new HashMap<>();

    public void addOrderBook(OrderBook orderbook) {
        orderBooksBySecurityId.put(orderbook.getSecurity().getId(), orderbook);
    }

    public void start() {
        Runnable queueConsumer = () -> {
            while (true) {
                if(!ordersToCancel.isEmpty()) {
                    final int cancelOrderId = ordersToCancel.poll();
                    ordersToCancel.remove(0);
                    if(orderBooksByOrderId.containsKey(cancelOrderId)) {
                        orderBooksByOrderId.get(cancelOrderId).cancelOrder(cancelOrderId);
                    } else {
                        ordersToAdd.removeIf(o -> o.getId() == cancelOrderId);
                    }
                }
                final Order nextOrder = ordersToAdd.poll();
                if (nextOrder == null) {
                    continue;
                }
                final OrderBook book = orderBooksBySecurityId.computeIfAbsent(nextOrder.getSecurity().getId(), k -> new OrderBook(securitiesById.get(nextOrder.getSecurity().getId()), this));
                book.addOrder(nextOrder);
                orderBooksByOrderId.put(nextOrder.getId(), book);
            }
        };
        new Thread(queueConsumer).start();
    }

    @Override
    public void submit(Order order) {
        ordersToAdd.put(order);
    }

    @Override
    public void cancel(int orderId) {  }

    public void printBook(int securityId) {
        System.out.println(orderBooksBySecurityId.get(securityId).toString());
    }

}
