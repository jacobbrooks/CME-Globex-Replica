package com.cme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

public class OrderBookTest {

    protected String getFailMessage(String message) {
        final StringBuilder builder = new StringBuilder();
        builder.append(message + "\n");
        return builder.toString();
    }

    protected String getFailMessage(String criteria, List<String> expected, List<String> actual) {
        final StringBuilder builder = new StringBuilder();
        builder.append("Expected " + criteria + ": [\n");
        expected.forEach(b -> builder.append("  " + b + ",\n"));
        builder.append("], \nActual " + criteria + ": [\n");
        actual.forEach(b -> builder.append("  " + b + ", \n"));
        builder.append("]\n\n");
        return builder.toString();
    }

    protected boolean equalMatches(List<MatchEvent> expected, List<MatchEvent> actual) {
        return actual.size() == expected.size()
                && IntStream.range(0, actual.size())
                .noneMatch(i -> actual.get(i).getRestingOrderId() != expected.get(i).getRestingOrderId()
                        || actual.get(i).getMatchQuantity() != expected.get(i).getMatchQuantity()
                        || actual.get(i).getMatchPrice() != expected.get(i).getMatchPrice());
    }

    protected void hold(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
