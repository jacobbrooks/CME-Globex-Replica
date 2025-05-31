package com.cme;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class OrderUpdate {

    private final Map<Long, List<MatchEvent>> matchesByPrice = new HashMap<>();

    private OrderStatus status;
    private OrderType type;
    private int remainingQuantity;

    public OrderUpdate(OrderStatus status, OrderType type) {
        this.status = status;
        this.type = type;
    }

    public void addMatches(long price, List<MatchEvent> matchEvents) {
        matchesByPrice.put(price, matchEvents);
    }

    public boolean isEmpty() {
        return matchesByPrice.isEmpty();
    }
}

