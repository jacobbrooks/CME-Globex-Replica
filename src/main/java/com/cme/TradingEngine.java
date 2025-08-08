package com.cme;

import lombok.Getter;
import lombok.Setter;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;

public class TradingEngine implements OrderService {

    private final PriorityBlockingQueue<Order> ordersToAdd = new PriorityBlockingQueue<>(1, Comparator.comparing(Order::getTimestamp));
    private final ConcurrentLinkedQueue<OrderCancel> ordersToCancel = new ConcurrentLinkedQueue<>();

    @Getter
    private final Map<Integer, OrderBook> orderBooksByOrderId = new ConcurrentHashMap<>();
    private final Map<Integer, OrderBook> orderBooksBySecurityId = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Setter
    ZonedDateTime nextExpirationTime = ZonedDateTime.now().withHour(23).withMinute(59).withSecond(0);

    public void addOrderBook(OrderBook orderbook) {
        orderBooksBySecurityId.put(orderbook.getSecurity().getId(), orderbook);
    }

    public void start() {
        scheduleExpirations();

        Runnable queueConsumer = () -> {
            while (true) {
                if(!ordersToCancel.isEmpty()) {
                    final OrderCancel orderCancel = ordersToCancel.poll();
                    if(orderBooksByOrderId.containsKey(orderCancel.getOrderId())) {
                        orderBooksByOrderId.get(orderCancel.getOrderId()).cancelOrder(orderCancel.getOrderId(), orderCancel.isExpired());
                        orderBooksByOrderId.remove(orderCancel.getOrderId());
                    } else {
                        ordersToAdd.removeIf(o -> o.getId() == orderCancel.getOrderId());
                    }
                }
                final Order nextOrder = ordersToAdd.poll();
                if (nextOrder == null) {
                    continue;
                }
                final OrderBook book = orderBooksBySecurityId.computeIfAbsent(nextOrder.getSecurity().getId(), k -> new OrderBook(nextOrder.getSecurity(), this));
                book.addOrder(nextOrder);
                orderBooksByOrderId.put(nextOrder.getId(), book);
            }
        };
        new Thread(queueConsumer).start();
    }

    private void scheduleExpirations() {
        final ZonedDateTime now = ZonedDateTime.now();

        if(now.compareTo(nextExpirationTime) > 0) {
            nextExpirationTime = nextExpirationTime.plusDays(1);
        }

        final Duration duration = Duration.between(now, nextExpirationTime);
        final long initialDelay = duration.getSeconds();

        scheduler.scheduleAtFixedRate(this::cancelExpiredOrders, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }

    public void cancelExpiredOrders() {
        orderBooksByOrderId.entrySet().stream()
                .map(e -> e.getValue().getOrders().get(e.getKey()))
                .filter(o -> o.shouldExpireToday(ZonedDateTime.now()))
                .map(o -> new OrderCancel(o.getId(), true))
                .forEach(this::cancel);
    }

    @Override
    public void submit(Order order) {
        ordersToAdd.put(order);
    }

    @Override
    public void cancel(OrderCancel orderCancel) {
        ordersToCancel.add(orderCancel);
    }

    public void cancel(int orderId) {
        cancel(new OrderCancel(orderId, false));
    }

    public void clear() {
        ordersToAdd.clear();
        ordersToCancel.clear();
        orderBooksBySecurityId.clear();
        orderBooksByOrderId.clear();
    }

    public void printBook(int securityId) {
        System.out.println(orderBooksBySecurityId.get(securityId).toString());
    }

}
