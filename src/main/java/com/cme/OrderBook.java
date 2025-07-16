package com.cme;

import lombok.Getter;

import java.util.*;

public class OrderBook {

    @Getter
    private final Security security;
    private final MatchStepComparator matchStepComparator;

    private final TreeMap<Long, PriceLevel> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, PriceLevel> asks = new TreeMap<>();

    @Getter
    private final Map<Integer, PriceLevel> priceLevelByOrderId = new HashMap<>();
    private final Map<String, Integer> orderIdByClientOrderId = new HashMap<>();
    private final Map<Integer, List<OrderUpdate>> orderUpdateMap = new HashMap<>();

    private final PriorityQueue<Order> stopOrders = new PriorityQueue<>(Comparator.comparingLong(Order::getTimestamp));
    private final Map<Integer, Order> icebergOrders = new HashMap<>();
    private final OrderScheduler orderScheduler;

    private Optional<Order> topBid = Optional.empty();
    private Optional<Order> topAsk = Optional.empty();

    private long lastTradedPrice;

    public OrderBook(Security security, OrderScheduler orderScheduler) {
        this.security = security;
        this.orderScheduler = orderScheduler;
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

        final TreeMap<Long, PriceLevel> matchAgainst = order.isBuy() ? asks : bids;
        final TreeMap<Long, PriceLevel> resting = order.isBuy() ? bids : asks;

        Optional<PriceLevel> best = Optional.ofNullable(matchAgainst.firstEntry()).map(Map.Entry::getValue);

        final long bestPrice = best.map(PriceLevel::getPrice).orElse(this.lastTradedPrice);

        if (order.isStopLimit() || order.isStopWithProtection()) {
            ack.setType(OrderType.StopLimit);
            stopOrders.add(order);
            return;
        }

        if(order.getDisplayQuantity() > 0) {
            icebergOrders.put(order.getId(), order);
        }

        final Order finalOrder = order.getDisplayQuantity() > 0 ? order.getNewSlice() : order;

        if(!minQuantityMet(finalOrder, matchAgainst)) {
            final OrderUpdate elimination = new OrderUpdate(OrderStatus.Expired, finalOrder.getOrderType());
            orderUpdateMap.get(finalOrder.getId()).add(elimination);
            return;
        }

        boolean lastTradedPriceUpdated = false;

        while (best.isPresent() && !finalOrder.isFilled() && isMatch(finalOrder, best.get(), bestPrice)) {
            this.lastTradedPrice = best.get().getPrice();
            lastTradedPriceUpdated = true;

            final List<MatchEvent> matches = best.get().match(finalOrder);

            final OrderUpdate aggressorFillNotice = new OrderUpdate(order.isFilled() ? OrderStatus.CompleteFill : OrderStatus.PartialFill, finalOrder.getOrderType());
            aggressorFillNotice.addMatches(lastTradedPrice, matches);
            aggressorFillNotice.setRemainingQuantity(finalOrder.getRemainingQuantity());
            orderUpdateMap.get(finalOrder.isSlice() ? finalOrder.getOriginId() : finalOrder.getId()).add(aggressorFillNotice);

            matches.forEach(m -> {
                final int remainingQty = Optional.ofNullable(priceLevelByOrderId.get(m.getRestingOrderId()).getOrder(m.getRestingOrderId()))
                        .map(Order::getRemainingQuantity).orElse(0);
                final OrderUpdate restingFillNotice = new OrderUpdate(remainingQty > 0 ? OrderStatus.PartialFill : OrderStatus.CompleteFill, finalOrder.getOrderType());
                restingFillNotice.addMatches(lastTradedPrice, List.of(m));
                restingFillNotice.setRemainingQuantity(remainingQty);
                orderUpdateMap.computeIfAbsent(m.getRestingOrderId(), k -> new ArrayList<OrderUpdate>()).add(restingFillNotice);
            });

            if (best.get().getTotalQuantity() == 0) {
                matchAgainst.pollFirstEntry();
            }

            best = Optional.ofNullable(matchAgainst.firstEntry()).map(Map.Entry::getValue);

            if(order.isMarketLimit()) {
                finalOrder.setOrderType(OrderType.Limit);
                finalOrder.setPrice(this.lastTradedPrice);
            }
        }

        if (finalOrder.isMarketWithProtection()) {
            finalOrder.setOrderType(OrderType.Limit);
            finalOrder.setPrice(order.isBuy() ? bestPrice + finalOrder.getProtectionPoints() : bestPrice - finalOrder.getProtectionPoints());
        }

        if (topBid.map(Order::isFilled).orElse(false)) {
            topBid = Optional.empty();
        }

        if (topAsk.map(Order::isFilled).orElse(false)) {
            topAsk = Optional.empty();
        }

        if (!finalOrder.isFilled() && finalOrder.getTimeInForce() != TimeInForce.FAK) {
            final PriceLevel addTo = resting.computeIfAbsent(finalOrder.getPrice(), k -> new PriceLevel(finalOrder.getPrice(),
                    security.getMatchingAlgorithm(), matchStepComparator, orderScheduler));

            if(finalOrder.isSlice()) {
                addTo.addIceberg(icebergOrders.get(finalOrder.getOriginId()));
            }

            final boolean deservesTopStatus = matchStepComparator.hasStep(MatchStep.TOP)
                    && finalOrder.getPrice() == resting.firstEntry().getKey()
                    && finalOrder.getRemainingQuantity() >= security.getTopMin()
                    && addTo.isEmpty();

            finalOrder.setTop(deservesTopStatus);
            addTo.add(finalOrder);
            priceLevelByOrderId.put(finalOrder.getId(), addTo);
            orderIdByClientOrderId.put(finalOrder.getClientOrderId(), finalOrder.getId());

            if (deservesTopStatus) {
                if (finalOrder.isBuy()) {
                    topBid.ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
                    topBid = Optional.of(finalOrder);
                } else {
                    topAsk.ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
                    topAsk = Optional.of(finalOrder);
                }
            }
        }

        if(!finalOrder.isFilled() && finalOrder.getTimeInForce() == TimeInForce.FAK) {
            final OrderUpdate elimination = new OrderUpdate(OrderStatus.Expired, finalOrder.getOrderType());
            orderUpdateMap.get(finalOrder.getId()).add(elimination);
        }

        if(finalOrder.isFilled() && finalOrder.isSlice() && !icebergOrders.get(finalOrder.getOriginId()).isFilled()) {
            orderScheduler.submit(icebergOrders.get(finalOrder.getOriginId()).getNewSlice());
            if(icebergOrders.get(finalOrder.getOriginId()).isFilled()) {
                icebergOrders.remove(finalOrder.getOriginId());
            }
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

    private boolean minQuantityMet(Order order, TreeMap<Long, PriceLevel> matchAgainst) {
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
        return priceLevelByOrderId.get(orderId).getOrder(orderId);
    }

    public List<Long> getBidPrices() { return bids.keySet().stream().toList(); }

    public List<Long> getAskPrices() { return asks.keySet().stream().toList(); }

    public void clear() {
        bids.clear();
        asks.clear();
        orderIdByClientOrderId.clear();
        priceLevelByOrderId.clear();
        stopOrders.clear();
        icebergOrders.clear();
        orderUpdateMap.clear();
        topBid = Optional.empty();
        topAsk = Optional.empty();
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
