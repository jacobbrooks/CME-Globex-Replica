package com.cme;

import com.cme.matchcomparators.MatchStepComparator;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class OrderBook {

    private final OrderService orderService;

    @Getter
    private final Security security;
    private final MatchStepComparator matchStepComparator;

    private final ConcurrentSkipListMap<Long, PriceLevel> bids = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    private final ConcurrentSkipListMap<Long, PriceLevel> asks = new ConcurrentSkipListMap<>();

    @Getter
    private final Map<Integer, Order> orders = new ConcurrentHashMap<>();
    private final Map<Integer, PriceLevel> priceLevelByOrderId = new ConcurrentHashMap<>();
    private final Map<String, Integer> orderIdByClientOrderId = new ConcurrentHashMap<>();
    private final Map<Integer, List<OrderUpdate>> orderUpdateMap = new ConcurrentHashMap<>();

    private final Queue<Order> stopOrders = new PriorityBlockingQueue<>(1, Comparator.comparingLong(Order::getTimestamp));
    private final Map<Integer, Order> icebergOrders = new HashMap<>();

    @Getter
    private final AtomicReference<Order> topBid = new AtomicReference<>();
    @Getter
    private final AtomicReference<Order> topAsk = new AtomicReference<>();

    private long lastTradedPrice;

    public OrderBook(Security security, OrderService orderService) {
        this.security = security;
        this.orderService = orderService;
        this.matchStepComparator = new MatchStepComparator(security.getMatchingAlgorithm());
    }

    /*
     * To be implemented
     */
    private boolean isValidOrder(Order order) {
        return true;
    }

    public void addOrder(Order order) {
        final OrderUpdate ack = new OrderUpdate(OrderStatus.New, order.getOrderType());
        orderUpdateMap.computeIfAbsent(order.getId(), k -> new ArrayList<OrderUpdate>()).add(ack);

        if(!isValidOrder(order)) {
            ack.setStatus(OrderStatus.Reject);
            return;
        }

        orders.put(order.getId(), order);

        final ConcurrentSkipListMap<Long, PriceLevel> matchAgainst = order.isBuy() ? asks : bids;
        final ConcurrentSkipListMap<Long, PriceLevel> resting = order.isBuy() ? bids : asks;

        Optional<PriceLevel> best = Optional.ofNullable(matchAgainst.firstEntry()).map(Map.Entry::getValue);

        final long bestPrice = best.map(PriceLevel::getPrice).orElse(this.lastTradedPrice);

        if (order.isStopLimit() || order.isStopWithProtection()) {
            ack.setType(OrderType.StopLimit);
            stopOrders.add(order);
            return;
        }

        if(order.isIceberg()) {
            icebergOrders.put(order.getId(), order);
        }

        final Order derivedOrder = order.getDisplayQuantity() > 0 ? order.getNewSlice() : order;
        orders.put(derivedOrder.getId(), derivedOrder);
        if(derivedOrder.isSlice()) {
            final OrderUpdate sliceAck = new OrderUpdate(OrderStatus.New, derivedOrder.getOrderType());
            orderUpdateMap.computeIfAbsent(derivedOrder.getId(), k -> new ArrayList<OrderUpdate>()).add(ack);
        }

        if(!minQuantityMet(derivedOrder, matchAgainst)) {
            final OrderUpdate elimination = new OrderUpdate(OrderStatus.Expired, derivedOrder.getOrderType());
            orderUpdateMap.get(derivedOrder.getId()).add(elimination);
            return;
        }

        boolean lastTradedPriceUpdated = false;

        while (best.isPresent() && !derivedOrder.isFilled() && isMatch(derivedOrder, best.get(), bestPrice)) {
            this.lastTradedPrice = best.get().getPrice();
            lastTradedPriceUpdated = true;

            final List<MatchEvent> matches = best.get().match(derivedOrder);

            final OrderUpdate aggressorFillNotice = new OrderUpdate(order.isFilled() ? OrderStatus.CompleteFill : OrderStatus.PartialFill, derivedOrder.getOrderType());
            aggressorFillNotice.addMatches(lastTradedPrice, matches);
            aggressorFillNotice.setRemainingQuantity(derivedOrder.getRemainingQuantity());
            orderUpdateMap.get(derivedOrder.isSlice() ? derivedOrder.getOriginId() : derivedOrder.getId()).add(aggressorFillNotice);

            matches.forEach(m -> {
                final int remainingQty = Optional.ofNullable(orders.get(m.getRestingOrderId())).map(Order::getRemainingQuantity).orElse(0);
                final OrderUpdate restingFillNotice = new OrderUpdate(remainingQty > 0 ? OrderStatus.PartialFill : OrderStatus.CompleteFill, derivedOrder.getOrderType());
                restingFillNotice.addMatches(lastTradedPrice, List.of(m));
                restingFillNotice.setRemainingQuantity(remainingQty);
                orderUpdateMap.computeIfAbsent(m.getRestingOrderId(), k -> new ArrayList<OrderUpdate>()).add(restingFillNotice);
                if(Optional.ofNullable(orders.get(m.getRestingOrderId())).map(Order::isFilled).orElse(true)) {
                    orders.remove(m.getRestingOrderId());
                    priceLevelByOrderId.remove(m.getRestingOrderId());
                }
            });

            if (best.get().getTotalQuantity() == 0) {
                matchAgainst.pollFirstEntry();
            }

            best = Optional.ofNullable(matchAgainst.firstEntry()).map(Map.Entry::getValue);

            if(order.isMarketLimit()) {
                derivedOrder.setOrderType(OrderType.Limit);
                derivedOrder.setPrice(this.lastTradedPrice);
            }
        }

        if (derivedOrder.isMarketWithProtection()) {
            derivedOrder.setOrderType(OrderType.Limit);
            derivedOrder.setPrice(order.isBuy() ? bestPrice + derivedOrder.getProtectionPoints() : bestPrice - derivedOrder.getProtectionPoints());
        }

        if (Optional.ofNullable(topBid.get()).map(Order::isFilled).orElse(false)) {
            topBid.set(null);
        }

        if (Optional.ofNullable(topAsk.get()).map(Order::isFilled).orElse(false)) {
            topAsk.set(null);
        }

        if (!derivedOrder.isFilled() && derivedOrder.getTimeInForce() != TimeInForce.FAK) {
            final PriceLevel addTo = resting.computeIfAbsent(derivedOrder.getPrice(), k -> new PriceLevel(derivedOrder.getPrice(),
                    security.getMatchingAlgorithm(), matchStepComparator, orderService));

            if(derivedOrder.isSlice()) {
                addTo.addIceberg(icebergOrders.get(derivedOrder.getOriginId()));
            }

            final boolean deservesTopStatus = matchStepComparator.hasStep(MatchStep.TOP)
                    && derivedOrder.getPrice() == resting.firstEntry().getKey()
                    && derivedOrder.getRemainingQuantity() >= security.getTopMin()
                    && addTo.isEmpty();

            derivedOrder.setTop(deservesTopStatus);
            addTo.add(derivedOrder);
            priceLevelByOrderId.put(derivedOrder.getId(), addTo);
            orderIdByClientOrderId.put(derivedOrder.getClientOrderId(), derivedOrder.getId());

            if (deservesTopStatus) {
                if (derivedOrder.isBuy()) {
                    Optional.ofNullable(topBid.get()).ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
                    topBid.set(derivedOrder);
                } else {
                    Optional.ofNullable(topAsk.get()).ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
                    topAsk.set(derivedOrder);
                }
            }
        }

        if(!derivedOrder.isFilled() && derivedOrder.getTimeInForce() == TimeInForce.FAK) {
            final OrderUpdate elimination = new OrderUpdate(OrderStatus.Expired, derivedOrder.getOrderType());
            orderUpdateMap.get(derivedOrder.getId()).add(elimination);
        }

        if(derivedOrder.isSlice()) {
            icebergOrders.get(derivedOrder.getOriginId()).fill(derivedOrder.getFilledQuantity());
        }

        final boolean parentIcebergFilled = Optional.ofNullable(icebergOrders.get(derivedOrder.getOriginId()))
                .map(Order::isFilled)
                .orElse(true);

        if(derivedOrder.isFilled() && derivedOrder.isSlice() && !parentIcebergFilled) {
            orderService.submit(icebergOrders.get(derivedOrder.getOriginId()).getNewSlice());
        }

        if (parentIcebergFilled) {
            icebergOrders.remove(derivedOrder.getOriginId());
        }

        if(derivedOrder.isFilled()) {
            orders.remove(derivedOrder.getId());
        }

        if(!lastTradedPriceUpdated) {
            return;
        }

        final List<Order> stopOrdersToTrigger = stopOrders.stream().filter(this::shouldTriggerStopOrder).toList();
        stopOrders.removeAll(stopOrdersToTrigger);

        // Trigger any stop orders
        stopOrdersToTrigger.forEach(o -> {
            if(o.isStopWithProtection()) {
                o.setPrice(o.isBuy() ? o.getTriggerPrice() + o.getProtectionPoints() : o.getTriggerPrice() - o.getProtectionPoints());
            }
            o.setOrderType(OrderType.Limit);
            addOrder(o);
        });
    }

    public void cancelOrder(int orderId, boolean expired) {
        stopOrders.removeIf(o -> o.getId() == orderId);
        icebergOrders.remove(orderId);

        if(!orders.containsKey(orderId)) {
            return;
        }

        final OrderUpdate update = new OrderUpdate(expired ? OrderStatus.Expired : OrderStatus.Cancelled, orders.get(orderId).getOrderType());
        orderUpdateMap.get(orders.get(orderId).getId()).add(update);

        orderIdByClientOrderId.remove(orders.get(orderId).getClientOrderId());

        // Cancel the order or any child slice if the order is an iceberg
        priceLevelByOrderId.entrySet().stream()
                .filter(e -> e.getKey() == orderId || orders.get(e.getKey()).getOriginId() == orderId)
                .findFirst()
                .ifPresent(e -> {
                    e.getValue().cancelOrder(e.getKey());
                    if(e.getValue().isEmpty() && orders.get(e.getKey()).isBuy()) {
                        bids.remove(e.getValue().getPrice());
                    } else if(e.getValue().isEmpty()) {
                        asks.remove(e.getValue().getPrice());
                    }
                });

        priceLevelByOrderId.entrySet().removeIf(e -> e.getKey() == orderId || orders.get(e.getKey()).getOriginId() == orderId);
        orders.entrySet().removeIf(e -> e.getKey() == orderId || orders.get(e.getKey()).getOriginId() == orderId);

        final boolean isTopBid = Optional.ofNullable(topBid.get()).map(b -> b.getId() == orderId).orElse(false);
        final boolean isTopAsk = Optional.ofNullable(topAsk.get()).map(b -> b.getId() == orderId).orElse(false);

        if(isTopBid) {
            topBid.set(null);
        } else if(isTopAsk) {
            topAsk.set(null);
        }
    }

    private boolean minQuantityMet(Order order, ConcurrentSkipListMap<Long, PriceLevel> matchAgainst) {
        return order.getMinQuantity() == 0 || order.getTimeInForce() != TimeInForce.FAK
                || matchAgainst.entrySet().stream().filter(e -> order.isBuy() ?
                        e.getKey() <= order.getPrice() :
                        e.getKey() >= order.getPrice())
                .map(Map.Entry::getValue)
                .mapToInt(PriceLevel::getTotalQuantity)
                .sum() >= order.getMinQuantity();
    }

    private boolean shouldTriggerStopOrder(Order order) {
        return order.isBuy() ? this.lastTradedPrice >= order.getTriggerPrice() : this.lastTradedPrice <= order.getTriggerPrice();
    }

    private static boolean isMatch(Order order, PriceLevel best, long bestPrice) {
        final boolean isMarketMatch = order.isMarketLimit() ||
                (order.isMarketWithProtection() && (order.isBuy() ?
                        best.getPrice() <= bestPrice + order.getProtectionPoints() :
                        best.getPrice() >= bestPrice - order.getProtectionPoints()));
        return isMarketMatch || (order.isBuy() ?
                best.getPrice() <= order.getPrice() :
                best.getPrice() >= order.getPrice());
    }

    public List<OrderUpdate> getOrderUpdates(int orderId) {
        return orderUpdateMap.get(orderId);
    }

    public OrderUpdate getLastOrderUpdate(int orderId) {
        return orderUpdateMap.get(orderId).get(orderUpdateMap.get(orderId).size() - 1);
    }

    public Order getOrder(String clientOrderId) {
        final int orderId = orderIdByClientOrderId.get(clientOrderId);
        return orders.get(orderId);
    }

    public List<Long> getBidPrices() { return bids.keySet().stream().toList(); }

    public List<Long> getAskPrices() { return asks.keySet().stream().toList(); }

    public void clear() {
        bids.clear();
        asks.clear();
        orders.clear();
        orderIdByClientOrderId.clear();
        priceLevelByOrderId.clear();
        stopOrders.clear();
        icebergOrders.clear();
        orderUpdateMap.clear();
        topBid.set(null);
        topAsk.set(null);
    }

    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("\n============ Bids ").append(bids.size()).append(" ==============\n");
        bids.values().stream().map(PriceLevel::toString).forEach(builder::append);
        builder.append("\n============ Asks ").append(asks.size()).append(" ==============\n");
        asks.values().stream().map(PriceLevel::toString).forEach(builder::append);
        return builder.toString();
    }

}
