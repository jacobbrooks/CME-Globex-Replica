package com.cme.matchcomparators;

import com.cme.Order;

import java.util.Comparator;

public class FIFOComparator implements Comparator<Order> {

    @Override
    public int compare(Order a, Order b) {
        return a.getTimestamp() <= b.getTimestamp() ? -1 : 1;
    }

}
