package com.cme;

import java.util.Comparator;

public class FIFOComparator implements Comparator<Order> {

    @Override
    public int compare(Order a, Order b) {
        return a.getTimestamp() <= b.getTimestamp() ? -1 : 1;
    }

}
