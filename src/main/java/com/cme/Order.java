package com.cme;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Builder
public class Order {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    private final int id = NEXT_ID.incrementAndGet();
    private final long timestamp = System.currentTimeMillis();

    @Builder.Default
    private final TimeInForce timeInForce = TimeInForce.Day;

    private final int originId;
    private final String clientOrderId;
    private final Security security;
    private final long triggerPrice;
    private final int initialQuantity;
    private final int minQuantity;
    private final int displayQuantity;
    private final boolean buy;

    @Setter
    @Builder.Default
    private OrderType orderType = OrderType.Limit;
    @Setter
    private long price;
    @Setter
    private boolean top;
    private int lmmAllocationPercentage;
    private int filledQuantity;
    private int currentStepInitialQuantity;
    private int remainingSplitFIFOQuantity;
    private double proration;
    private boolean lmmAllocated;
    private boolean proRataAllocated;
    private boolean markedForLeveling;
    private boolean slice;

    public static class OrderBuilder {
        public OrderBuilder initialQuantity(int initialQuantity) {
            this.currentStepInitialQuantity = this.initialQuantity = initialQuantity;
            return this;
        }
    }

    public void fill(int quantity) {
        fill(quantity, null);
    }

    public void fill(int quantity, MatchStep matchStep) {
        filledQuantity += quantity;
        if(matchStep == null) {
            return;
        }
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

    public Order getNewSlice() {
        if(this.slice || isFilled()) {
            return null;
        }
        filledQuantity += displayQuantity;
        return builder().originId(id).timeInForce(timeInForce).clientOrderId(clientOrderId)
                .security(security).triggerPrice(triggerPrice).initialQuantity(displayQuantity)
                .minQuantity(minQuantity).buy(buy).orderType(orderType).price(price).slice(true).build();
    }

    public void markForLeveling() {
        this.markedForLeveling = true;
    }

    public int getRemainingQuantity() {
        return initialQuantity - filledQuantity;
    }

    public int getProtectionPoints() { return security.getProtectionPoints(); }

    public boolean isFilled() {
        return getRemainingQuantity() == 0;
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
        return orderType == OrderType.StopLimit;
    }

    public boolean isStopWithProtection() {
        return orderType == OrderType.StopWithProtection;
    }

    public boolean isIceberg() {
        return displayQuantity == 0;
    }

    //public Order to

    public String toString() {
        return "#" + id + " - " + getRemainingQuantity() + " @" + timestamp + "ms, " + lmmAllocationPercentage + "%";
    }

}
