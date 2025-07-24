package com.cme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class OrderFunctionalitiesTest extends OrderBookTest {

    /*
     * We are testing on an OrderBook for a security that uses the Configurable matching
     * algorithm specifically because it is a complex matching algorithm with multiple steps. We
     * want to prove that the internal state of the book & prioritization of the orders
     * remain intact after invasive/unnatural actions like order cancels & modifications.
     */
    private final Security configurable = Security.builder().id(1)
            .matchingAlgorithm(MatchingAlgorithm.Configurable)
            .proRataMin(2).splitPercentage(40).build();

    private final TradingEngine engine = new TradingEngine();
    private final OrderBook orderBook = new OrderBook(configurable, engine);

    @Test
    public void testOrderCancel() {
        // Pairs are {qty, lmmPercentage}
        List<Order> bids = Stream.of(new int[]{2, 0}, new int[]{100, 20}, new int[]{51, 10},
                new int[]{47, 20}, new int[]{100, 0}, new int[]{1, 0}, new int[]{1, 0},
                new int[]{1, 0}, new int[]{1, 0}).map(pair -> {
            hold(10);
            return Order.builder().clientOrderId(Integer.toString(0))
                    .security(configurable)
                    .buy(true)
                    .price(100L)
                    .initialQuantity(pair[0])
                    .lmmAllocationPercentage(pair[1])
                    .build();
        }).toList();

        bids.forEach(orderBook::addOrder);

        // We will immediately cancel the 2nd bid with the hope that the state of the book remains consistent
        // allowing the rest of the test to pass
        orderBook.cancelOrder(bids.get(1).getId());

        List<Order> top = bids.stream().filter(Order::isTop).toList();
        String topOrderId = "Order id: " + top.stream()
                .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

        if (top.size() != 1) {
            fail(getFailMessage("TOP event 1: Not exactly 1 top order"));
        }

        if (!bids.get(0).isTop()) {
            fail(getFailMessage("TOP event 1: Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(configurable)
                .buy(false)
                .price(100L)
                .initialQuantity(202)
                .lmmAllocationPercentage(0)
                .build();

        orderBook.addOrder(ask);
        OrderUpdate response = orderBook.getLastOrderUpdate(ask.getId());

        /*
         * 1. TOP pass - order 0 should match for 2 lots -> aggressor qty = 200
         * 2. LMM pass - 10% LMM order should receive 10% * 200 = 20 lots -> aggressor qty = 180
         * 3. LMM pass - 20% LMM order should receive 20% * 200 = 40 lots -> aggressor qty = 140
         * 4. Split FIFO pass - 40% of the aggressor qty (40% * 140 = 56) should go to the earliest
         *       order in the book (order 1) which only has 31 lots available -> aggressor qty = 109,
         *       remaining splitFIFO qty = 25
         * 5. Split FIFO pass - 7 of 25 Remaining lots are assigned to the next earliest (order 2)
         *       -> aggressor qty = 102, remaining splitFIFO qty = 18
         * 6. Split FIFO pass - 18 remaining lots are assigned to the next earliest (order 3)
         *       -> aggressor qty = 84
         * 7. Pro Rata pass - Total resting qty on the book is 86 lots, order 3 has the biggest
         *       proportion with 82 lots, and is filled for 84 * (82 / 86) = 80 lots
         *       -> aggressor qty = 4
         * 8. Pro Rata pass - orders 4,5,6 & 7 all have the next biggest proportions, each with 1 lot,
         *       84 * (1 / 86) = 0.9767 lots which is rounded down to 0, so they are all marked for leveling
         * 9. Leveling pass - Order 4,5,6 & 7 are assigned 1 lot in that order due to qty/time priority
         *       -> aggressor qty = 0
         */
        List<MatchEvent> matches = response.getMatches();
        List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 2, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 20, false, 0L),
                new MatchEvent(ask.getId(), bids.get(3).getId(), 100L, 40, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 31, false, 0L),
                new MatchEvent(ask.getId(), bids.get(3).getId(), 100L, 7, false, 0L),
                new MatchEvent(ask.getId(), bids.get(4).getId(), 100L, 18, false, 0L),
                new MatchEvent(ask.getId(), bids.get(4).getId(), 100L, 80, false, 0L),
                new MatchEvent(ask.getId(), bids.get(5).getId(), 100L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(6).getId(), 100L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(7).getId(), 100L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(8).getId(), 100L, 1, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 1", expectedMatches.stream()
                    .map(MatchEvent::toString).toList(), matches.stream()
                    .map(MatchEvent::toString).toList()));
        }

        // Now let's test without LMMs
        bids = Stream.of(1, 100, 30, 80, 30, 60).map(qty -> {
            hold(10);
            return Order.builder().clientOrderId(Integer.toString(0))
                    .security(configurable)
                    .buy(true)
                    .price(200L)
                    .initialQuantity(qty)
                    .build();
        }).toList();


        bids.forEach(orderBook::addOrder);

        top = bids.stream().filter(Order::isTop).toList();
        topOrderId = "Order id: " + top.stream()
                .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

        if (top.size() != 1) {
            fail(getFailMessage("TOP event 2: Not exactly 1 top order - " + top.size()));
        }

        if (!bids.get(0).isTop()) {
            fail(getFailMessage("TOP event 2: Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(configurable)
                .buy(false)
                .price(200L)
                .initialQuantity(8)
                .build();

        orderBook.addOrder(ask);
        response = orderBook.getLastOrderUpdate(ask.getId());

        /*
         * 1. TOP Pass - Order 0 is filled for 1 lot
         * 2. Split FIFO Pass - 40% of the remaining aggressor qty of 7 = 3 which all goes to order 1
         * 3. Pro Rata pass - Remaining aggressor qty of 4 * each order's proration leaves orders 1 & 3
         *       being allocated 1 lot, and all other orders (2, 4 & 5) rounded down to 0 and
         *       marked for leveling
         * 4. Leveling pass - 1-lot leveling applies first to order 5 (qty priority), then order 2 (time priority),
         *    but there is no more aggressing qty to fulfill order 4's 1 lot leveling.
         */
        matches = response.getMatches();
        expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 200L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 200L, 3, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 200L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(3).getId(), 200L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(5).getId(), 200L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 200L, 1, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 2", expectedMatches.stream()
                    .map(MatchEvent::toString).toList(), matches.stream()
                    .map(MatchEvent::toString).toList()));
        }
    }

}
