package com.cme;

import com.cme.matchcomparators.OrderModify;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
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
                    processOrderCancel();
                }

                final Order nextOrder = ordersToAdd.poll();
                if (nextOrder == null) {
                    continue;
                }

                final OrderBook book = orderBooksBySecurityId.computeIfAbsent(nextOrder.getSecurity().getId(), k -> new OrderBook(nextOrder.getSecurity(), this));

                if(nextOrder.isInFlightMitigatedReplacement()) {
                    final int filledQtyBetweenModifyRequestAndCancel = book.getOrderUpdates(nextOrder.getOriginId()).stream()
                            .filter(u -> u.getTimestamp() > nextOrder.getTimestamp())
                            .filter(u -> u.getStatus() == OrderStatus.PartialFill || u.getStatus() == OrderStatus.CompleteFill)
                            .mapToInt(u -> u.getMatches().stream().mapToInt(MatchEvent::getMatchQuantity).sum())
                            .sum();
                    final int mitigatedQty = Math.min(0, nextOrder.getInitialQuantity() - filledQtyBetweenModifyRequestAndCancel);
                    nextOrder.setInitialQuantity(mitigatedQty);
                }

                if(nextOrder.getInitialQuantity() > 0) {
                    book.addOrder(nextOrder);
                    orderBooksByOrderId.put(nextOrder.getId(), book);
                }
            }
        };
        new Thread(queueConsumer).start();
    }

    private void processOrderCancel() {
        final OrderCancel orderCancel = ordersToCancel.poll();
        if(orderBooksByOrderId.containsKey(orderCancel.getOrderId())) {
            orderBooksByOrderId.get(orderCancel.getOrderId()).cancelOrder(orderCancel.getOrderId(), orderCancel.isExpired());
            orderBooksByOrderId.remove(orderCancel.getOrderId());
        } else {
            ordersToAdd.removeIf(o -> o.getId() == orderCancel.getOrderId());
        }
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

    private void cancelExpiredOrders() {
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

    @Override
    public void modify(OrderModify orderModify) {
        final Order original = orderBooksByOrderId.get(orderModify.getOrderId()).getOrders().get(orderModify.getOrderId());
        final Order modified = Order.builder().originId(original.getId())
                .inFlightMitigatedReplacement(Optional.ofNullable(orderModify.getInFlightMitigation()).orElse(false))
                .clientOrderId(Optional.ofNullable(orderModify.getClientOrderId()).orElse(original.getClientOrderId()))
                .initialQuantity(Optional.ofNullable(orderModify.getQuantity()).orElse(original.getRemainingQuantity()))
                .orderType(Optional.ofNullable(orderModify.getOrderType()).orElse(original.getOrderType()))
                .price(Optional.ofNullable(orderModify.getPrice()).orElse(original.getPrice()))
                .timeInForce(Optional.ofNullable(orderModify.getTimeInForce()).orElse(original.getTimeInForce()))
                .triggerPrice(Optional.ofNullable(orderModify.getTriggerPrice()).orElse(original.getTriggerPrice()))
                .minQuantity(Optional.ofNullable(orderModify.getMinQuantity()).orElse(original.getMinQuantity()))
                .displayQuantity(Optional.ofNullable(orderModify.getDisplayQuantity()).orElse(original.getDisplayQuantity()))
                .expiration(Optional.ofNullable(orderModify.getExpiration()).orElse(original.getExpiration()))
                .security(original.getSecurity())
                .buy(original.isBuy())
                .build();
        cancel(original.getId());
        submit(modified);
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
