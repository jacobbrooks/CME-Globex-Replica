package com.cme;

import java.util.*;

public class OrderBook {

    private final Security security;
    private final TreeMap<Long, PriceLevel> bids;
    private final TreeMap<Long, PriceLevel> asks;
    private final Map<Integer, PriceLevel> priceLevelByOrderId;
    private final Map<String, Integer> orderIdByClientOrderId;
    private final PriorityQueue<Order> stopOrders;

    private final Map<Integer, List<OrderUpdate>> orderResponseMap;

    private Optional<Order> currentTopBid;
    private Optional<Order> currentTopAsk;

    private final MatchStepComparator matchStepComparator;

    private long lastTradedPrice;

    public OrderBook(Security security) {
        this.security = security;
        this.bids = new TreeMap<Long, PriceLevel>(Collections.reverseOrder());
        this.asks = new TreeMap<Long, PriceLevel>();
        this.priceLevelByOrderId = new HashMap<Integer, PriceLevel>();
        this.orderIdByClientOrderId = new HashMap<String, Integer>();
        this.currentTopBid = Optional.empty();
        this.currentTopAsk = Optional.empty();
        this.matchStepComparator = new MatchStepComparator(security.getMatchingAlgorithm());
        this.stopOrders = new PriorityQueue<>(Comparator.comparingLong(Order::getTimestamp));
        this.orderResponseMap = new HashMap<>();
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
        final OrderUpdate update = new OrderUpdate(OrderStatus.New);
        orderResponseMap.computeIfAbsent(order.getId(),k -> new ArrayList<OrderUpdate>()).add(update);

        if(!isValidOrder(order)) {
            update.setStatus(OrderStatus.Reject);
            return;
        }

        final TreeMap<Long, PriceLevel> matchAgainst = order.isBuy() ? asks : bids;
        final TreeMap<Long, PriceLevel> resting = order.isBuy() ? bids : asks;

        Optional<PriceLevel> best = Optional.ofNullable(matchAgainst.firstEntry()).map(Map.Entry::getValue);

        final long bestPrice = best.map(PriceLevel::getPrice).orElse(this.lastTradedPrice);

        if (order.isStopLimit() || order.isStopWithProtection()) {
            stopOrders.add(order);
            return;
        }

        while (best.isPresent() && !order.isFilled()) {
            final boolean isMarketMatch = order.isMarketLimit() ||
                    (order.isMarketWithProtection() && (order.isBuy() ?
                            best.get().getPrice() < bestPrice + order.getProtectionPoints() :
                            best.get().getPrice() > bestPrice - order.getProtectionPoints()));

            final boolean isMatch = isMarketMatch || (order.isBuy() ?
                    best.get().getPrice() <= order.getPrice() :
                    best.get().getPrice() >= order.getPrice());

            if (!isMatch) {
                break;
            }

            final List<MatchEvent> matches = best.get().match(order);
            update.addMatches(best.get().getPrice(), matches);

            matches.forEach(m -> {
                //final OrderUpdate fillNotice = new OrderUpdate(OrderStatus.Filled);
                orderResponseMap.computeIfAbsent(m.getRestingOrderId(), k -> new ArrayList<OrderUpdate>()).add(update);
            });

            this.lastTradedPrice = best.get().getPrice();

            if (best.get().getTotalQuantity() == 0) {
                matchAgainst.pollFirstEntry();
            }

            best = Optional.ofNullable(matchAgainst.firstEntry()).map(Map.Entry::getValue);

            if (print) {
                matches.forEach(System.out::println);
            }
        }

        if (order.isMarketWithProtection()) {
            order.setPrice(order.isBuy() ? bestPrice + order.getProtectionPoints() : bestPrice - order.getProtectionPoints());
        }

        if (currentTopBid.map(Order::isFilled).orElse(false)) {
            currentTopBid = Optional.empty();
        }

        if (currentTopAsk.map(Order::isFilled).orElse(false)) {
            currentTopAsk = Optional.empty();
        }

        if (order.isFilled()) {
            return;
        }

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

        if (!deservesTopStatus) {
            return;
        }

        if (order.isBuy()) {
            currentTopBid.ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
            currentTopBid = Optional.of(order);
        } else {
            currentTopAsk.ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
            currentTopAsk = Optional.of(order);
        }

        // Trigger any stop orders
        stopOrders.stream().filter(o -> this.lastTradedPrice >= o.getTriggerPrice())
                .map(o -> o.toLimitOrder(o))
                .forEach(this::addOrder);

        stopOrders.removeIf(o -> this.lastTradedPrice >= o.getTriggerPrice());
    }

    public List<OrderUpdate> getOrderResponses(int orderId) {
        return orderResponseMap.get(orderId);
    }

    public OrderUpdate getLastOrderResponse(int orderId) {
        return orderResponseMap.get(orderId).get(orderResponseMap.get(orderId).size() - 1);
    }

    public Order getOrder(String clientOrderId) {
        final int orderId = orderIdByClientOrderId.get(clientOrderId);
        return priceLevelByOrderId.get(orderId).getOrder(orderId);
    }

    public List<Long> getBidPrices() {
        return bids.keySet().stream().toList();
    }

    public List<Long> getAskPrices() { return asks.keySet().stream().toList(); }

    public void clear() {
        bids.clear();
        asks.clear();
        orderIdByClientOrderId.clear();
        priceLevelByOrderId.clear();
        stopOrders.clear();
        orderResponseMap.clear();
        currentTopBid = Optional.empty();
        currentTopAsk = Optional.empty();
    }

    public void printBook() {
        System.out.println("============ Bids " + bids.size() + " ==============");
        bids.values().stream().map(PriceLevel::toString).forEach(System.out::println);
        System.out.println("============ Asks " + asks.size() + " ==============");
        asks.values().stream().map(PriceLevel::toString).forEach(System.out::println);
    }

}
