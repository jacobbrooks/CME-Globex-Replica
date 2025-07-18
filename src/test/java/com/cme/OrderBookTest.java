package com.cme;

import java.util.List;
import java.util.stream.IntStream;

public class OrderBookTest {

    protected String getFailMessage(String message) {
        return message + "\n";
    }

    protected String getFailMessage(String criteria, List<String> expected, List<String> actual) {
        final StringBuilder builder = new StringBuilder();
        builder.append("Expected ").append(criteria).append(": [\n");
        expected.forEach(b -> builder.append("  ").append(b).append(",\n"));
        builder.append("], \nActual ").append(criteria).append(": [\n");
        actual.forEach(b -> builder.append("  ").append(b).append(", \n"));
        builder.append("]\n\n");
        return builder.toString();
    }

    protected boolean equalMatches(List<MatchEvent> expected, List<MatchEvent> actual) {
        return actual.size() == expected.size()
                && IntStream.range(0, actual.size())
                .noneMatch(i -> actual.get(i).getRestingOrderId() != expected.get(i).getRestingOrderId()
                        || actual.get(i).getAggressingOrderId() != expected.get(i).getAggressingOrderId()
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
