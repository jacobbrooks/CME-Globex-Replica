package com.cme;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class OrderUpdate {

    private final List<MatchEvent> matches = new ArrayList<>();

    private OrderStatus status;
    private OrderType type;
    private int remainingQuantity;
    private long price;

    public OrderUpdate(OrderStatus status, OrderType type) {
        this.status = status;
        this.type = type;
    }

    public void addMatches(long price, List<MatchEvent> matchEvents) {
        this.price = price;
        matches.addAll(matchEvents);
    }

    public boolean isEmpty() {
        return matches.isEmpty();
    }
}

