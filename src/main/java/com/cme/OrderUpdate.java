package com.cme;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class OrderUpdate {

    @Setter
    private OrderStatus status;

    private final Map<Long, List<MatchEvent>> matchesByPrice;
    private final Map<Integer, Integer> remainingQtyByOrderId;

    public OrderUpdate(OrderStatus status) {
        this.status = status;
        matchesByPrice = new HashMap<>();
        remainingQtyByOrderId = new HashMap<>();
    }

    public void addMatches(long price, List<MatchEvent> matchEvents) {
        matchesByPrice.put(price, matchEvents);
    }

    public boolean isEmpty() {
        return matchesByPrice.isEmpty();
    }
}

