package com.cme;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OrderUpdate {

    private final long timestamp = System.currentTimeMillis();

    private final List<MatchEvent> matches = new ArrayList<>();

    private OrderStatus status;
    private OrderType aggressingOrderType;
    private int remainingQuantity;
    private long price;

    public OrderUpdate(OrderStatus status, OrderType aggressingOrderType) {
        this.status = status;
        this.aggressingOrderType = aggressingOrderType;
    }

    public void addMatches(long price, List<MatchEvent> matchEvents) {
        this.price = price;
        matches.addAll(matchEvents);
    }

    public boolean isEmpty() {
        return matches.isEmpty();
    }
}

