package com.cme;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class Order {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    private final int id;
    private final String clientOrderId;
    private final Security security;
    private final long timestamp;
    private final long protectionPoints;
    private final long triggerPrice;
    private final int initialQuantity;
    private final boolean buy;
    private final OrderType orderType;
    private final OrderDuration orderDuration;

    @Setter
    private long price;
    private int lmmAllocationPercentage;
    private int filledQuantity;
    private int currentStepInitialQuantity;
    private int remainingSplitFIFOQuantity;
    private double proration;

    @Setter
    private boolean top;
    private boolean lmmAllocated;
    private boolean proRataAllocated;
    private boolean markedForLeveling;

    public Order(String clientOrderId, Security security, boolean buy, long price, int initialQuantity, int lmmAllocationPercentage) {
        this.id = NEXT_ID.incrementAndGet();
        this.clientOrderId = clientOrderId;
        this.security = security;
        this.timestamp = System.currentTimeMillis();
        this.buy = buy;
        this.price = price;
        this.initialQuantity = initialQuantity;
        this.lmmAllocationPercentage = lmmAllocationPercentage;
        this.currentStepInitialQuantity = initialQuantity;
        this.orderType = OrderType.Limit;
        this.orderDuration = OrderDuration.Day;
        this.protectionPoints = 0;
        this.triggerPrice = 0;
    }

    public Order(String clientOrderId, Security security, boolean buy, long price, int initialQuantity, int lmmAllocationPercentage, OrderType orderType, OrderDuration orderDuration, long protectionPoints, long triggerPrice) {
        this.id = NEXT_ID.incrementAndGet();
        this.clientOrderId = clientOrderId;
        this.security = security;
        this.timestamp = System.currentTimeMillis();
        this.buy = buy;
        this.price = price;
        this.initialQuantity = initialQuantity;
        this.lmmAllocationPercentage = lmmAllocationPercentage;
        this.currentStepInitialQuantity = initialQuantity;
        this.orderType = orderType;
        this.orderDuration = orderDuration;
        this.protectionPoints = protectionPoints;
        this.triggerPrice = triggerPrice;
    }

    public void fill(int quantity, MatchStep matchStep) {
        filledQuantity += quantity;
        if (matchStep == MatchStep.LMM && !lmmAllocated) {
            lmmAllocated = true;
        }
        if (matchStep == MatchStep.ProRata && !proRataAllocated) {
            proRataAllocated = true;
        }
        if (matchStep == MatchStep.Leveling && markedForLeveling) {
            markedForLeveling = false;
        }
        if (remainingSplitFIFOQuantity > 0) {
            remainingSplitFIFOQuantity -= quantity;
        }
    }

    public void updateProration(int totalPriceLevelQuantity) {
        if (totalPriceLevelQuantity <= 0 || proRataAllocated) {
            return;
        }
        this.proration = (double) getRemainingQuantity() / totalPriceLevelQuantity;
    }

    public void resetMatchingAlgorithmFlags() {
        this.lmmAllocated = false;
        this.proRataAllocated = false;
        this.markedForLeveling = false;
    }

    public void setInitialQuantityForNextStep() {
        this.currentStepInitialQuantity = getRemainingQuantity();
    }

    public void setInitialSplitFIFOQuantity() {
        this.remainingSplitFIFOQuantity = (int) Math.round((security.getSplitPercentage()
                * (double) getRemainingQuantity()) / 100);
    }

    public void markForLeveling() {
        this.markedForLeveling = true;
    }

    public int getLMMAllocationPercentage() {
        return lmmAllocationPercentage;
    }

    public int getRemainingQuantity() {
        return initialQuantity - filledQuantity;
    }

    public boolean isFilled() {
        return getRemainingQuantity() == 0;
    }

    public boolean isLMMAllocated() {
        return lmmAllocated;
    }

    public boolean isLMMAllocatable() {
        return lmmAllocationPercentage > 0 && !lmmAllocated;
    }

    public boolean isProRataAllocatable() {
        return proration > 0 && !proRataAllocated;
    }

    public boolean isMarketLimit() {
        return orderType == OrderType.MarketLimit;
    }

    public boolean isMarketWithProtection() {
        return orderType == OrderType.MarketWithProtection;
    }

    public boolean isStopLimit() {
        return orderType == OrderType.Stop;
    }

    public boolean isStopWithProtection() {
        return orderType == OrderType.StopWithProtection;
    }

    public String toString() {
        return "#" + id + " - " + getRemainingQuantity() + " @" + timestamp + "ms, " + lmmAllocationPercentage + "%";
    }

}
