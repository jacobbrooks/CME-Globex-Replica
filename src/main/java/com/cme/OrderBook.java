package com.cme;

import java.util.*;

public class OrderBook {

    private final Security security;
    private final MatchStepComparator matchStepComparator;

    private final TreeMap<Long, PriceLevel> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Map<Integer, PriceLevel> priceLevelByOrderId = new HashMap<>();
    private final Map<String, Integer> orderIdByClientOrderId = new HashMap<>();
    private final Map<Integer, List<OrderUpdate>> orderUpdateMap = new HashMap<>();
    private final PriorityQueue<Order> stopOrders = new PriorityQueue<>(Comparator.comparingLong(Order::getTimestamp));

    private Optional<Order> currentTopBid = Optional.empty();
    private Optional<Order> currentTopAsk = Optional.empty();

    private long lastTradedPrice;

    public OrderBook(Security security) {
        this.security = security;
        this.matchStepComparator = new MatchStepComparator(security.getMatchingAlgorithm());
    }

    public void addOrder(Order order) {
        addOrder(order, false);
    }

    /*
     * To be implemented
     */
    private boolean isValidOrder(Order order) {
        return true;
    }

    public void addOrder(Order order, boolean print) {
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

        final boolean sufficientQuantity = order.getMinQuantity() == 0
                || matchAgainst.entrySet().stream().filter(e -> order.isBuy() ?
                        e.getKey() <= order.getPrice() : e.getKey() >= order.getPrice())
                    .map(Map.Entry::getValue)
                    .mapToInt(PriceLevel::getTotalQuantity)
                    .sum() >= order.getMinQuantity();

        if(!sufficientQuantity) {
            final OrderUpdate elimination = new OrderUpdate(OrderStatus.Expired, order.getOrderType());
            orderUpdateMap.get(order.getId()).add(elimination);
            return;
        }

        boolean lastTradedPriceUpdated = false;

        while (best.isPresent() && !order.isFilled()) {
            if (!isMatch(order, best.get(), bestPrice)) {
                break;
            }

            this.lastTradedPrice = best.get().getPrice();
            lastTradedPriceUpdated = true;

            final List<MatchEvent> matches = best.get().match(order);

            final OrderUpdate aggressorFillNotice = new OrderUpdate(order.isFilled() ? OrderStatus.CompleteFill : OrderStatus.PartialFill, order.getOrderType());
            aggressorFillNotice.addMatches(lastTradedPrice, matches);
            aggressorFillNotice.setRemainingQuantity(order.getRemainingQuantity());
            orderUpdateMap.get(order.getId()).add(aggressorFillNotice);

            matches.forEach(m -> {
                final int remainingQty = Optional.ofNullable(priceLevelByOrderId.get(m.getRestingOrderId()).getOrder(m.getRestingOrderId()))
                        .map(Order::getRemainingQuantity).orElse(0);
                final OrderUpdate restingFillNotice = new OrderUpdate(remainingQty > 0 ? OrderStatus.PartialFill : OrderStatus.CompleteFill, order.getOrderType());
                restingFillNotice.addMatches(lastTradedPrice, List.of(m));
                restingFillNotice.setRemainingQuantity(remainingQty);
                orderUpdateMap.computeIfAbsent(m.getRestingOrderId(), k -> new ArrayList<OrderUpdate>()).add(restingFillNotice);
            });

            if (best.get().getTotalQuantity() == 0) {
                matchAgainst.pollFirstEntry();
            }

            best = Optional.ofNullable(matchAgainst.firstEntry()).map(Map.Entry::getValue);

            if(order.isMarketLimit()) {
                order.setOrderType(OrderType.Limit);
                order.setPrice(this.lastTradedPrice);
            }

            if (print) {
                matches.forEach(System.out::println);
            }
        }

        if (order.isMarketWithProtection()) {
            order.setOrderType(OrderType.Limit);
            order.setPrice(order.isBuy() ? bestPrice + order.getProtectionPoints() : bestPrice - order.getProtectionPoints());
        }

        if (currentTopBid.map(Order::isFilled).orElse(false)) {
            currentTopBid = Optional.empty();
        }

        if (currentTopAsk.map(Order::isFilled).orElse(false)) {
            currentTopAsk = Optional.empty();
        }

        if (!order.isFilled() && order.getTimeInForce() != TimeInForce.FAK) {
            final PriceLevel addTo = resting.computeIfAbsent(order.getPrice(), k -> new PriceLevel(order.getPrice(),
                    security.getMatchingAlgorithm(), matchStepComparator));

            final boolean deservesTopStatus = matchStepComparator.hasStep(MatchStep.TOP)
                    && order.getPrice() == resting.firstEntry().getKey()
                    && order.getRemainingQuantity() >= security.getTopMin()
                    && addTo.isEmpty();

            order.setTop(deservesTopStatus);
            addTo.add(order);
            priceLevelByOrderId.put(order.getId(), addTo);
            orderIdByClientOrderId.put(order.getClientOrderId(), order.getId());

            if (deservesTopStatus) {
                if (order.isBuy()) {
                    currentTopBid.ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
                    currentTopBid = Optional.of(order);
                } else {
                    currentTopAsk.ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
                    currentTopAsk = Optional.of(order);
                }
            }
        } else if(!order.isFilled()) {
            // FAK order gets eliminated instead of resting on the book
            final OrderUpdate elimination = new OrderUpdate(OrderStatus.Expired, order.getOrderType());
            orderUpdateMap.get(order.getId()).add(elimination);
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
        orderUpdateMap.clear();
        currentTopBid = Optional.empty();
        currentTopAsk = Optional.empty();
    }

    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    public void printBook() {
        System.out.println("============ Bids " + bids.size() + " ==============");
        bids.values().stream().map(PriceLevel::toString).forEach(System.out::println);
        System.out.println("============ Asks " + asks.size() + " ==============");
        asks.values().stream().map(PriceLevel::toString).forEach(System.out::println);
    }

}
