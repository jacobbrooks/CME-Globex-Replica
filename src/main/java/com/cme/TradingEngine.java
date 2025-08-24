package com.cme;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;

public class TradingEngine implements OrderService {

    @Getter
    private final OrderUpdateService orderUpdateService = new OrderUpdateService();

    private final ConcurrentLinkedQueue<Order> ordersToAdd = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OrderCancel> ordersToCancel = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OrderModify> ordersToModify = new ConcurrentLinkedQueue<>();

    @Getter
    private final Map<Integer, OrderBook> orderBooksByOrderId = new ConcurrentHashMap<>();
    private final Map<Integer, OrderBook> orderBooksBySecurityId = new ConcurrentHashMap<>();
    private final Map<Integer, OrderBell> orderBells = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> processHolds = new ConcurrentHashMap<>();

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

                if(!ordersToModify.isEmpty()) {
                    processOrderModify();
                }

                final Order nextOrder = ordersToAdd.poll();
                if (nextOrder == null) {
                    continue;
                }

                if(onProcessHold(nextOrder.getId())) {
                    ordersToAdd.add(nextOrder);
                    continue;
                }

                final OrderBook book = orderBooksBySecurityId.computeIfAbsent(nextOrder.getSecurity().getId(), k -> new OrderBook(nextOrder.getSecurity(), this, orderUpdateService));

                book.addOrder(nextOrder);
                orderBooksByOrderId.put(nextOrder.getId(), book);

                orderBells.get(nextOrder.getId()).ring();
                if(nextOrder.isIceberg()) {
                    ringIcebergSliceOrderBells(nextOrder, book);
                }
            }
        };
        new Thread(queueConsumer).start();
    }


    @Override
    public void add(Order order) {
        orderBells.computeIfAbsent(order.getId(), k -> new OrderBell());
        if(order.isSlice()) {
            orderBells.get(order.getOriginId()).silence();
        }
        ordersToAdd.add(order);
    }

    @Override
    public void cancel(OrderCancel orderCancel) {
        orderBells.get(orderCancel.getOrderId()).silence();
        ordersToCancel.add(orderCancel);
    }

    @Override
    public void modify(OrderModify orderModify) {
        orderBells.get(orderModify.getOrderId()).silence();
        orderModify.setRestingQuantity(orderBooksByOrderId.get(orderModify.getOrderId()).getOrders().get(orderModify.getOrderId()).getRemainingQuantity());
        ordersToModify.add(orderModify);
    }

    private void processOrderModify() {
        final OrderModify orderModify = ordersToModify.poll();
        final Order original = orderBooksByOrderId.get(orderModify.getOrderId()).getOrders().get(orderModify.getOrderId());

        if(original == null || original.getRemainingQuantity() == 0) {
            OrderUpdate reject = new OrderUpdate(OrderStatus.Reject, null);
            orderUpdateService.pushOrderUpdate(orderModify.getOrderId(), reject);
            orderBells.get(orderModify.getOrderId()).ring();
            return;
        }

        if(onProcessHold(original.getId())) {
            ordersToModify.add(orderModify);
            return;
        }

        final Order modified = Order.builder()
                .originId(original.getId())
                .replacement(true)
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

        if(modified.isInFlightMitigatedReplacement()) {
            final int filledQtyBetweenModifyRequestAndCancel = orderModify.getRestingQuantity() - original.getRemainingQuantity();
            final int mitigatedQty = Math.max(0, modified.getInitialQuantity() - filledQtyBetweenModifyRequestAndCancel);
            modified.setInitialQuantity(mitigatedQty);
        }

        final OrderBook book = orderBooksByOrderId.get(orderModify.getOrderId());

        book.cancelOrder(original.getId(), false);

        final List<Order> unaddedIcebergSlicesToRing = ordersToAdd.stream()
                .filter(o -> o.getOriginId() == original.getId()).toList();
        ordersToAdd.removeIf(o -> o.getId() == original.getId() || o.getOriginId() == original.getId());
        unaddedIcebergSlicesToRing.forEach(o -> orderBells.get(o.getId()).ring());

        if(modified.getInitialQuantity() > 0) {
            book.addOrder(modified);
        }

        orderBells.get(original.getId()).ring();
        if(original.isIceberg()) {
            ringIcebergSliceOrderBells(original, book);
        }

        if(modified.getInitialQuantity() > 0) {
            orderBells.computeIfAbsent(modified.getId(), k -> new OrderBell()).ring();
        }
        if(modified.isIceberg() && modified.getInitialQuantity() > 0) {
            ringIcebergSliceOrderBells(modified, book);
        }
    }

    private void ringIcebergSliceOrderBells(Order order, OrderBook book) {
        final int sliceId = book.getActiveSliceByIceberg().get(order.getId());
        orderBells.computeIfAbsent(sliceId, k -> new OrderBell()).ring();
    }

    private void processOrderCancel() {
        final OrderCancel orderCancel = ordersToCancel.poll();
        if(onProcessHold(orderCancel.getOrderId())) {
            ordersToCancel.add(orderCancel);
            return;
        }
        if(orderBooksByOrderId.containsKey(orderCancel.getOrderId())) {
            orderBooksByOrderId.get(orderCancel.getOrderId()).cancelOrder(orderCancel.getOrderId(), orderCancel.isExpired());
            orderBooksByOrderId.remove(orderCancel.getOrderId());
        } else {
            ordersToAdd.removeIf(o -> o.getId() == orderCancel.getOrderId());
        }
        orderBells.get(orderCancel.getOrderId()).ring();
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

    public void waitForOrderBell(int orderId) {
        try {
            orderBells.computeIfAbsent(orderId, k -> new OrderBell()).waitForRing();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void placeOnProcessHold(int orderId) {
        processHolds.put(orderId, true);
    }

    public void removeProcessHold(int orderId) {
        processHolds.remove(orderId);
    }

    public boolean onProcessHold(int orderId) {
        return Optional.ofNullable(processHolds.get(orderId)).orElse(false);
    }

    public void cancel(int orderId) {
        cancel(new OrderCancel(orderId, false));
    }

    public void clear() {
        ordersToAdd.clear();
        ordersToCancel.clear();
        orderBooksBySecurityId.clear();
        orderBooksByOrderId.clear();
        orderBells.clear();
    }

    public void printBook(int securityId) {
        System.out.println(orderBooksBySecurityId.get(securityId).toString());
    }

    @Getter
    private static final class OrderBell {
        private boolean rang = false;
        public synchronized void waitForRing() throws InterruptedException {
            if(!rang) {
                wait();
            }
            rang = false;
        }
        public synchronized void ring() {
            rang = true;
            notify();
        }
        public synchronized void silence() {
            rang = false;
        }
    }

}
