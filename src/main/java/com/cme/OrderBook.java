package com.cme;

import com.cme.matchcomparators.MatchStepComparator;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class OrderBook {

    private final OrderService orderService;
    private final OrderUpdateService orderUpdateService;

    @Getter
    private final Security security;
    private final MatchStepComparator matchStepComparator;

    private final ConcurrentSkipListMap<Long, PriceLevel> bids = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    private final ConcurrentSkipListMap<Long, PriceLevel> asks = new ConcurrentSkipListMap<>();

    @Getter
    private final Map<Integer, Order> orders = new ConcurrentHashMap<>();
    private final Map<Integer, PriceLevel> priceLevelByOrderId = new ConcurrentHashMap<>();
    private final Map<String, Integer> orderIdByClientOrderId = new ConcurrentHashMap<>();

    private final Queue<Order> stopOrders = new PriorityBlockingQueue<>(1, Comparator.comparingLong(Order::getTimestamp));
    private final Map<Integer, Order> icebergOrders = new ConcurrentHashMap<>();

    @Getter
    private final Map<Integer, Integer> activeSliceByIceberg = new ConcurrentHashMap<>();

    @Getter
    private final AtomicReference<Order> topBid = new AtomicReference<>();
    @Getter
    private final AtomicReference<Order> topAsk = new AtomicReference<>();

    private long lastTradedPrice;

    public OrderBook(Security security, OrderService orderService, OrderUpdateService orderUpdateService) {
        this.security = security;
        this.orderService = orderService;
        this.matchStepComparator = new MatchStepComparator(security.getMatchingAlgorithm());
        this.orderUpdateService = orderUpdateService;
    }

    /*
     * To be implemented
     */
    private boolean isValidOrder(Order order) {
        return true;
    }

    public void addOrder(Order order) {
        final OrderUpdate ack = new OrderUpdate(OrderStatus.New, order.getOrderType());
        pushOrderUpdate(order.getId(), ack);

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
            ack.setAggressingOrderType(OrderType.StopLimit);
            stopOrders.add(order);
            return;
        }

        if(order.isIceberg()) {
            icebergOrders.put(order.getId(), order);
        }

        final Order derivedOrder = order.getDisplayQuantity() > 0 ? order.getNewSlice() : order;
        if(order.getDisplayQuantity() > 0) {
            orders.put(derivedOrder.getId(), derivedOrder);
            final OrderUpdate sliceAck = new OrderUpdate(OrderStatus.New, derivedOrder.getOrderType());
            pushOrderUpdate(derivedOrder.getId(), ack);
        }

        if(derivedOrder.isSlice()) {
            activeSliceByIceberg.put(derivedOrder.getOriginId(), derivedOrder.getId());
        }

        if(!minQuantityMet(derivedOrder, matchAgainst)) {
            final OrderUpdate elimination = new OrderUpdate(OrderStatus.Expired, derivedOrder.getOrderType());
            pushOrderUpdate(derivedOrder.getId(), elimination);
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
            pushOrderUpdate(derivedOrder.getId(), aggressorFillNotice);

            final Set<Integer> ordersToRemove = matches.stream().map(e -> {
                final Order match = orders.get(e.getRestingOrderId());
                final int remainingQty = match.getRemainingQuantity();
                final OrderUpdate restingFillNotice = new OrderUpdate(remainingQty > 0 ? OrderStatus.PartialFill : OrderStatus.CompleteFill, derivedOrder.getOrderType());
                restingFillNotice.addMatches(lastTradedPrice, List.of(e));
                restingFillNotice.setRemainingQuantity(remainingQty);
                pushOrderUpdate(e.getRestingOrderId(), restingFillNotice);
                if(match.isSlice()) {
                    processIcebergMatch(match, List.of(e));
                }
                return remainingQty == 0 ? e.getRestingOrderId() : null;
            }).filter(Objects::nonNull).collect(Collectors.toSet());

            ordersToRemove.forEach(id -> {
                orders.remove(id);
                priceLevelByOrderId.remove(id);
            });

            if (best.get().getTotalQuantity() == 0) {
                matchAgainst.pollFirstEntry();
            }

            best = Optional.ofNullable(matchAgainst.firstEntry()).map(Map.Entry::getValue);

            if(order.isMarketLimit()) {
                derivedOrder.setOrderType(OrderType.Limit);
                derivedOrder.setPrice(this.lastTradedPrice);
            }

            processIcebergMatch(derivedOrder, matches);
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
                    security.getMatchingAlgorithm(), matchStepComparator));

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
            pushOrderUpdate(derivedOrder.getId(), elimination);
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

    public void processIcebergMatch(Order matchedSlice, List<MatchEvent> matches) {
        if(!matchedSlice.isSlice()) {
            return;
        }

        icebergOrders.get(matchedSlice.getOriginId()).fill(matchedSlice.getFilledQuantity());

        final int icebergRemainingQty = orders.get(matchedSlice.getOriginId()).getRemainingQuantity();
        final OrderUpdate icebergFillNotice = new OrderUpdate(icebergRemainingQty > 0 ? OrderStatus.PartialFill : OrderStatus.CompleteFill, matchedSlice.getOrderType());
        icebergFillNotice.addMatches(lastTradedPrice, matches);
        icebergFillNotice.setRemainingQuantity(icebergRemainingQty);
        pushOrderUpdate(matchedSlice.getOriginId(), icebergFillNotice);

        final boolean parentIcebergFilled = Optional.ofNullable(icebergOrders.get(matchedSlice.getOriginId()))
                .map(Order::isFilled)
                .orElse(true);

        if (parentIcebergFilled) {
            icebergOrders.remove(matchedSlice.getOriginId());
            return;
        }

        if(matchedSlice.isFilled()) {
            orderService.submit(icebergOrders.get(matchedSlice.getOriginId()).getNewSlice());
        }
    }

    public void cancelOrder(int orderId, boolean expired) {
        stopOrders.removeIf(o -> o.getId() == orderId);
        icebergOrders.remove(orderId);

        if(!orders.containsKey(orderId)) {
            return;
        }

        final OrderUpdate update = new OrderUpdate(expired ? OrderStatus.Expired : OrderStatus.Cancelled, orders.get(orderId).getOrderType());
        pushOrderUpdate(orders.get(orderId).getId(), update);
        if(orders.get(orderId).isIceberg()) {
            pushOrderUpdate(activeSliceByIceberg.get(orderId), update);
        }

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

    public boolean hasOrder(int orderId) {
        return orders.containsKey(orderId)
                || icebergOrders.containsKey(orderId)
                || stopOrders.stream().anyMatch(o -> o.getId() == orderId);
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

    private void pushOrderUpdate(int orderId, OrderUpdate update) {
        orderUpdateService.pushOrderUpdate(orderId, update);
    }

    public List<OrderUpdate> getOrderUpdates(int orderId) {
        return orderUpdateService.getOrderUpdates(orderId);
    }

    public OrderUpdate getLastOrderUpdate(int orderId) {
        final List<OrderUpdate> updates = getOrderUpdates(orderId);
        return updates.get(updates.size() - 1);
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
        activeSliceByIceberg.clear();
        orderUpdateService.clear();
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
