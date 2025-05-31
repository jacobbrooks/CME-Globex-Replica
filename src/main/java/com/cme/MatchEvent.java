package com.cme;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MatchEvent {

    private final int aggressingOrderId;
    private final int restingOrderId;
    private final long matchPrice;
    private final int matchQuantity;
    private final boolean aggressorBuySide;
    private final long timestamp;

    public String toString() {
        final String aggressor = aggressorBuySide ? "buy" : "sell";
        final String resting = aggressorBuySide ? "sell" : "buy";
        return "Match: " + aggressor + "(" + aggressingOrderId + ") -> " + resting + "(" + restingOrderId + "), qty=" + matchQuantity + " @" + matchPrice;
    }
}
