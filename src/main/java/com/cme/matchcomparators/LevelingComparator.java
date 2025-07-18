package com.cme.matchcomparators;

import com.cme.Order;

public class LevelingComparator extends FIFOComparator {

    @Override
    public int compare(Order a, Order b) {
        if (a.isMarkedForLeveling() && !b.isMarkedForLeveling()) {
            return -1;
        }
        if (b.isMarkedForLeveling() && !a.isMarkedForLeveling()) {
            return 1;
        }
        final int delta = b.getRemainingQuantity() - a.getRemainingQuantity();
        return delta == 0 ? super.compare(a, b) : delta;
    }

}
