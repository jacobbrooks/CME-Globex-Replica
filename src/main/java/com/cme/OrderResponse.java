package com.cme;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderResponse {

    @Getter
    private final Map<Long, List<MatchEvent>> matchesByPrice;

    public OrderResponse() {
        matchesByPrice = new HashMap<Long, List<MatchEvent>>();
    }

    public void addMatches(long price, List<MatchEvent> matchEvents) {
        matchesByPrice.put(price, matchEvents);
    }

    public boolean isEmpty() {
        return matchesByPrice.isEmpty();
    }
}

