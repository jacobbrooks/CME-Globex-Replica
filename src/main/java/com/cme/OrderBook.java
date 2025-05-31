package com.cme;

import java.util.*;

public class OrderBook {

    private final Security security;
    private final MatchStepComparator matchStepComparator;

    private final TreeMap<Long, PriceLevel> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, PriceLevel> asks = new TreeMap<>();
    private final Map<Integer, PriceLevel> priceLevelByOrderId = new HashMap<>();
    private final Map<String, Integer> orderIdByClientOrderId = new HashMap<>();
    private final Map<Integer, List<OrderUpdate>> orderResponseMap = new HashMap<>();
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
        orderResponseMap.computeIfAbsent(order.getId(),k -> new ArrayList<OrderUpdate>()).add(ack);

        if(!isValidOrder(order)) {
            ack.setStatus(OrderStatus.Reject);
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
            if (!isMatch(order, best.get(), bestPrice)) {
                break;
            }

            this.lastTradedPrice = best.get().getPrice();

            final List<MatchEvent> matches = best.get().match(order);

            final OrderUpdate aggressorFillNotice = new OrderUpdate(OrderStatus.Filled, order.getOrderType());
            aggressorFillNotice.addMatches(lastTradedPrice, matches);
            aggressorFillNotice.setRemainingQuantity(order.getRemainingQuantity());
            orderResponseMap.get(order.getId()).add(aggressorFillNotice);

            matches.forEach(m -> {
                final OrderUpdate restingFillNotice = new OrderUpdate(OrderStatus.Filled, order.getOrderType());
                final int remainingQty = Optional.ofNullable(priceLevelByOrderId.get(m.getRestingOrderId()).getOrder(m.getRestingOrderId()))
                        .map(Order::getRemainingQuantity).orElse(0);
                restingFillNotice.addMatches(lastTradedPrice, List.of(m));
                restingFillNotice.setRemainingQuantity(remainingQty);
                orderResponseMap.computeIfAbsent(m.getRestingOrderId(), k -> new ArrayList<OrderUpdate>()).add(restingFillNotice);
            });

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

        if (!order.isFilled()) {
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
        }

        final List<Order> stopOrdersToTrigger = stopOrders.stream().filter(o -> this.lastTradedPrice >= o.getTriggerPrice()).toList();
        stopOrders.removeAll(stopOrdersToTrigger);

        // Trigger any stop orders
        stopOrdersToTrigger.forEach(o -> {
            o.setOrderType(OrderType.Limit);
            addOrder(o);
        });
    }

    private static boolean isMatch(Order order, PriceLevel best, long bestPrice) {
        final boolean isMarketMatch = order.isMarketLimit() ||
                (order.isMarketWithProtection() && (order.isBuy() ?
                        best.getPrice() < bestPrice + order.getProtectionPoints() :
                        best.getPrice() > bestPrice - order.getProtectionPoints()));
        return isMarketMatch || (order.isBuy() ?
                best.getPrice() <= order.getPrice() :
                best.getPrice() >= order.getPrice());
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
