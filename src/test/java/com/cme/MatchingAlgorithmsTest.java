package com.cme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

public class MatchingAlgorithmsTest extends OrderBookTest {

    private final Security fifo = Security.builder().id(1).matchingAlgorithm(MatchingAlgorithm.FIFO).build();
    private final Security lmm = Security.builder().id(1).matchingAlgorithm(MatchingAlgorithm.LMM).build();
    private final Security lmmTop = Security.builder().id(1).matchingAlgorithm(MatchingAlgorithm.LMMWithTOP).build();
    private final Security proRata = Security.builder().id(1).matchingAlgorithm(MatchingAlgorithm.ProRata).build();
    private final Security allocation = Security.builder().id(1).matchingAlgorithm(MatchingAlgorithm.Allocation).build();
    private final Security configurable = Security.builder().id(1).matchingAlgorithm(MatchingAlgorithm.Configurable)
            .proRataMin(2).splitPercentage(40).build();
    private final Security configurableNoFIFO = Security.builder().id(1).matchingAlgorithm(MatchingAlgorithm.Configurable)
            .proRataMin(2).build();
    private final Security configurableNoProRata = Security.builder().id(1).matchingAlgorithm(MatchingAlgorithm.Configurable)
            .proRataMin(2).splitPercentage(100).build();
    private final Security thresholdProRata = Security.builder().id(1)
            .matchingAlgorithm(MatchingAlgorithm.ThresholdProRata).topMin(10).topMax(100).proRataMin(1).build();
    private final Security thresholdProRataWithLMM = Security.builder().id(1)
            .matchingAlgorithm(MatchingAlgorithm.ThresholdProRataWithLMM).topMin(25).topMax(250).proRataMin(1).build();

    private final OrderUpdateService orderUpdateService = new OrderUpdateService();

    private final OrderBook fifoOrderBook = new OrderBook(fifo, null, orderUpdateService);
    private final OrderBook lmmOrderBook = new OrderBook(lmm, null, orderUpdateService);
    private final OrderBook lmmTopOrderBook = new OrderBook(lmmTop, null, orderUpdateService);
    private final OrderBook proRataOrderBook = new OrderBook(proRata, null, orderUpdateService);
    private final OrderBook allocationOrderBook = new OrderBook(allocation, null, orderUpdateService);
    private final OrderBook configurableOrderBook = new OrderBook(configurable, null, orderUpdateService);
    private final OrderBook configurableNoFIFOOrderBook = new OrderBook(configurableNoFIFO, null, orderUpdateService);
    private final OrderBook configurableNoProRataOrderBook = new OrderBook(configurableNoProRata, null, orderUpdateService);
    private final OrderBook thresholdProRataOrderBook = new OrderBook(thresholdProRata, null, orderUpdateService);
    private final OrderBook thresholdProRataWithLMMOrderBook = new OrderBook(thresholdProRataWithLMM, null, orderUpdateService);

    @Test
    public void testThresholdProRataWithLMMOrderBook() {
        // Pairs are {qty, lmmPercentage}
        final List<Order> bids = Stream.of(new int[]{300, 0}, new int[]{15, 0}, new int[]{160, 40})
                .map(pair -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(thresholdProRataWithLMM)
                            .buy(true)
                            .price(100L)
                            .initialQuantity(pair[0])
                            .lmmAllocationPercentage(pair[1])
                            .build();
                }).toList();

        bids.forEach(thresholdProRataWithLMMOrderBook::addOrder);

        final List<Order> top = bids.stream().filter(Order::isTop).toList();
        final String topOrderId = "Order id: " + top.stream()
                .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

        if (top.size() != 1) {
            fail(getFailMessage("TOP event 1: Not exactly 1 top order"));
        }

        if (!bids.get(0).isTop()) {
            fail(getFailMessage("TOP event 1: Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        final Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(thresholdProRataWithLMM)
                .buy(false)
                .price(100L)
                .initialQuantity(400)
                .lmmAllocationPercentage(0)
                .build();

        thresholdProRataWithLMMOrderBook.addOrder(ask);
        final OrderUpdate response = thresholdProRataWithLMMOrderBook.getLastOrderUpdate(ask.getId());

        final List<MatchEvent> matches = response.getMatches();
        final List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 250, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 60, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 54, false, 0L),
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 27, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 8, false, 0L),
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 1", expectedMatches.stream()
                    .map(MatchEvent::toString).toList(), matches.stream()
                    .map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testThresholdProRataOrderBook() {
        final List<Order> bids = Stream.of(150, 8, 160)
                .map(qty -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(thresholdProRata)
                            .buy(true)
                            .price(100L)
                            .initialQuantity(qty)
                            .lmmAllocationPercentage(0)
                            .build();
                }).toList();

        bids.forEach(thresholdProRataOrderBook::addOrder);

        final List<Order> top = bids.stream().filter(Order::isTop).toList();
        final String topOrderId = "Order id: " + top.stream()
                .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

        if (top.size() != 1) {
            fail(getFailMessage("Not exactly 1 top order"));
        }

        if (!bids.get(0).isTop()) {
            fail(getFailMessage("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        final Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(thresholdProRata)
                .buy(false)
                .price(100L)
                .initialQuantity(200)
                .lmmAllocationPercentage(0)
                .build();

        thresholdProRataOrderBook.addOrder(ask);
        final OrderUpdate response = thresholdProRataOrderBook.getLastOrderUpdate(ask.getId());

        final List<MatchEvent> matches = response.getMatches();
        final List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 100, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 73, false, 0L),
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 22, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 3, false, 0L),
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 2, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 1", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testConfigurableNoProRataOrderBook() {
        // Pairs are {qty, lmmPercentage}
        final List<Order> bids = Stream.of(new int[]{1, 0}, new int[]{59, 20}, new int[]{40, 10}).map(pair -> {
            hold(10);
            return Order.builder().clientOrderId(Integer.toString(0))
                    .security(configurableNoProRata)
                    .buy(true)
                    .price(100L)
                    .initialQuantity(pair[0])
                    .lmmAllocationPercentage(pair[1])
                    .build();
        }).toList();

        bids.forEach(configurableNoProRataOrderBook::addOrder);

        final List<Order> top = bids.stream().filter(Order::isTop).toList();
        final String topOrderId = "Order id: " + top.stream()
                .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

        if (top.size() != 1) {
            fail(getFailMessage("TOP event 1: Not exactly 1 top order"));
        }

        if (!bids.get(0).isTop()) {
            fail(getFailMessage("TOP event 1: Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        final Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(configurableNoProRata)
                .buy(false)
                .price(100L)
                .initialQuantity(50)
                .lmmAllocationPercentage(0)
                .build();

        configurableNoProRataOrderBook.addOrder(ask);
        final OrderUpdate response = configurableNoProRataOrderBook.getLastOrderUpdate(ask.getId());

        /*
         * TOP pass - Order 0 is filled for its 1 lot - aggressor qty = 49
         * LMM pass - Order 1 is filled for its LMM allocation 20% * 49 = 9, aggressor qty = 40
         * LMM pass - Order 2 is filled for its LMM allocation 10% * 49 = 4, aggressor qty = 36
         * Split FIFO Pass - Order 1 has 50 remaining qty and is the earliest in the book, and
         *    therefore receives all 100% * 36 remaining aggressor lots
         */
        final List<MatchEvent> matches = response.getMatches();
        final List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 9, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 4, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 36, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 1", expectedMatches.stream()
                    .map(MatchEvent::toString).toList(), matches.stream()
                    .map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testConfigurableNoFIFOOrderBook() {
        // Pairs are {qty, lmmPercentage}
        final List<Order> bids = Stream.of(new int[]{1, 0}, new int[]{59, 20}, new int[]{40, 10}).map(pair -> {
            hold(10);
            return Order.builder().clientOrderId(Integer.toString(0))
                    .security(configurableNoFIFO)
                    .buy(true)
                    .price(100L)
                    .initialQuantity(pair[0])
                    .lmmAllocationPercentage(pair[1])
                    .build();
        }).toList();

        bids.forEach(configurableNoFIFOOrderBook::addOrder);

        final List<Order> top = bids.stream().filter(Order::isTop).toList();
        final String topOrderId = "Order id: " + top.stream()
                .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

        if (top.size() != 1) {
            fail(getFailMessage("TOP event 1: Not exactly 1 top order"));
        }

        if (!bids.get(0).isTop()) {
            fail(getFailMessage("TOP event 1: Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        final Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(configurableNoFIFO)
                .buy(false)
                .price(100L)
                .initialQuantity(50)
                .lmmAllocationPercentage(0)
                .build();

        configurableNoFIFOOrderBook.addOrder(ask);
        final OrderUpdate response = configurableNoFIFOOrderBook.getLastOrderUpdate(ask.getId());

        /*
         * TOP pass - Order 0 is filled for its 1 lot - aggressor qty = 49
         * LMM pass - Order 1 is filled for its LMM allocation 20% * 49 = 9, aggressor qty = 40
         * LMM pass - Order 2 is filled for its LMM allocation 10% * 49 = 4, aggressor qty = 36
         * Split FIFO Pass - 0% aggressor qty is configured for SplitFIFO pass so we move on to ProRata
         * Pro Rata pass - Total resting qty = 86, order 1 has the largest share at 50, so it is allocated
         *    (50 / 86) * 36 = 20 lots
         * Pro Rata pass - Order 2 has the next largest share at 36 so it is allocated
         *    (36 / 86) * 36 = 15 lots, aggressor qty = 1
         * FIFO pass - Order 1 is the earliest order in the book and is assigned the last aggressor lot
         */
        final List<MatchEvent> matches = response.getMatches();
        final List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 9, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 4, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 20, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 15, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 1, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 1", expectedMatches.stream()
                    .map(MatchEvent::toString).toList(), matches.stream()
                    .map(MatchEvent::toString).toList()));
        }

        /*
         * Unrelated to the core objective of this test, but I just want to make sure that if a price level
         * that previously had a TOP order was completely filled, it is eligible to contain another TOP order
         * (obviously given that no better price level exists).
         */
        final Order oneMoreTOP = Order.builder().clientOrderId(Integer.toString(0))
                .security(configurableNoFIFO)
                .buy(true)
                .price(100L)
                .initialQuantity(1)
                .lmmAllocationPercentage(0)
                .build();

        configurableNoFIFOOrderBook.addOrder(oneMoreTOP);

        if (oneMoreTOP.isTop()) {
            fail(getFailMessage("TOP event 2: Not exactly 1 top order"));
        }
    }

    @Test
    public void testConfigurableOrderBook() {
        // Pairs are {qty, lmmPercentage}
        List<Order> bids = Stream.of(new int[]{2, 0}, new int[]{51, 10},
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

        bids.forEach(configurableOrderBook::addOrder);

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

        configurableOrderBook.addOrder(ask);
        OrderUpdate response = configurableOrderBook.getLastOrderUpdate(ask.getId());

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
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 20, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 40, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 31, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 7, false, 0L),
                new MatchEvent(ask.getId(), bids.get(3).getId(), 100L, 18, false, 0L),
                new MatchEvent(ask.getId(), bids.get(3).getId(), 100L, 80, false, 0L),
                new MatchEvent(ask.getId(), bids.get(4).getId(), 100L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(5).getId(), 100L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(6).getId(), 100L, 1, false, 0L),
                new MatchEvent(ask.getId(), bids.get(7).getId(), 100L, 1, false, 0L)
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


        bids.forEach(configurableOrderBook::addOrder);

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

        configurableOrderBook.addOrder(ask);
        response = configurableOrderBook.getLastOrderUpdate(ask.getId());

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

    @Test
    public void testAllocationOrderBook() {
        final List<Order> bids = Stream.of(2, 56, 42)
                .map(qty -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(allocation)
                            .buy(true)
                            .price(100L)
                            .initialQuantity(qty)
                            .build();
                }).toList();

        bids.forEach(allocationOrderBook::addOrder);

        List<Order> top = bids.stream().filter(Order::isTop).toList();
        final String topOrderId = "Order id: " + top.stream()
                .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

        if (top.size() != 1) {
            fail(getFailMessage("Not exactly 1 top order"));
        }

        if (!bids.get(0).isTop()) {
            fail(getFailMessage("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(allocation)
                .buy(false)
                .price(100L)
                .initialQuantity(50)
                .build();

        allocationOrderBook.addOrder(ask);
        OrderUpdate response = allocationOrderBook.getLastOrderUpdate(ask.getId());

        List<MatchEvent> matches = response.getMatches();
        List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 2, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 27, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 20, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 1, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 1", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }

        // There should now be 50 resting quantity on the book, let's hit it with one more aggressor
        ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(allocation)
                .buy(false)
                .price(100L)
                .initialQuantity(50)
                .build();

        allocationOrderBook.addOrder(ask);
        response = allocationOrderBook.getLastOrderUpdate(ask.getId());
        matches = response.getMatches();
        expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 28, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 22, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 2", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testProRataOrderBook() {
        final List<Order> bids = Stream.of(2, 42, 56)
                .map(qty -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(proRata)
                            .buy(true)
                            .price(100L)
                            .initialQuantity(qty)
                            .build();
                }).toList();

        bids.forEach(proRataOrderBook::addOrder);

        Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(proRata)
                .buy(false)
                .price(100L)
                .initialQuantity(50)
                .build();

        proRataOrderBook.addOrder(ask);
        OrderUpdate response = proRataOrderBook.getLastOrderUpdate(ask.getId());

        List<MatchEvent> matches = response.getMatches();
        List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 28, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 21, false, 0L),
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 1", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }

        ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(proRata)
                .buy(false)
                .price(100L)
                .initialQuantity(50)
                .build();

        proRataOrderBook.addOrder(ask);
        response = proRataOrderBook.getLastOrderUpdate(ask.getId());

        matches = response.getMatches();
        expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 28, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 21, false, 0L),
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 2", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testLMMWithTopOrderBook() {
        // Top order should be the first to market
        final List<Order> bids = Stream.of(0, 10)
                .map(p -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(lmmTop)
                            .buy(true)
                            .price(100L)
                            .initialQuantity(10)
                            .lmmAllocationPercentage(p)
                            .build();
                }).toList();

        bids.forEach(lmmTopOrderBook::addOrder);

        List<Order> top = bids.stream().filter(Order::isTop).toList();
        final String topOrderId = "Order id: " + top.stream()
                .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

        if (top.size() != 1) {
            fail(getFailMessage("Not exactly 1 top order"));
        }

        if (!bids.get(0).isTop()) {
            fail(getFailMessage("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        /*
         * First to best the market should be the new top, so the first order at this 200
         * price level should dethrone the original top order
         */
        final List<Order> higherBids = Stream.of(0, 10, 20, 0)
                .map(p -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(lmmTop)
                            .buy(true)
                            .price(200L)
                            .initialQuantity(10)
                            .lmmAllocationPercentage(p)
                            .build();
                }).toList();

        higherBids.forEach(lmmTopOrderBook::addOrder);

        top = Stream.concat(bids.stream().filter(Order::isTop), higherBids.stream().filter(Order::isTop)).toList();
        if (top.size() != 1) {
            fail(getFailMessage("Not exactly 1 top order"));
        }

        if (!higherBids.get(0).isTop() || bids.get(0).isTop()) {
            fail(getFailMessage("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        final Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(lmmTop)
                .buy(false)
                .price(200L)
                .initialQuantity(40)
                .build();

        /*
         * We expect the $200x40 ask to first match against the TOP order for 10 lots. (30 lots remain)
         *
         * The next two orders are from LMMs and are both guaranteed their LMM allocations, so it doesn't
         * matter who is filled first. We break the tie by whoever is earliest in the book, which is:
         *
         * the 10% LMM order fot its LMM allocation percentage (10% * 30 lots = 3 lots), then
         * the 20% LMM order for its LMM allocation percentage (20% * 30 lots = 6 lots)
         *
         * We then FIFO match against the 10% LMM order for 7 remaining lots since it's the earliest remaining,
         * then a FIFO match against the 20% LMM order for 4 remaining lots since it's the next earliest remaining,
         *
         * then lastly a FIFO match the last order for the all 10 lots.
         */
        lmmTopOrderBook.addOrder(ask);
        final OrderUpdate response = lmmTopOrderBook.getLastOrderUpdate(ask.getId());
        List<MatchEvent> matches = response.getMatches();
        List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), higherBids.get(0).getId(), 200L, 10, false, 0L),
                new MatchEvent(ask.getId(), higherBids.get(1).getId(), 200L, 3, false, 0L),
                new MatchEvent(ask.getId(), higherBids.get(2).getId(), 200L, 6, false, 0L),
                new MatchEvent(ask.getId(), higherBids.get(1).getId(), 200L, 7, false, 0L),
                new MatchEvent(ask.getId(), higherBids.get(2).getId(), 200L, 4, false, 0L),
                new MatchEvent(ask.getId(), higherBids.get(3).getId(), 200L, 10, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testLMMOrderBookOneLeft() {
        lmmOrderBook.clear();

        final List<Order> bids = Stream.of(50, 60)
                .map(p -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(lmm)
                            .buy(true)
                            .price(100L)
                            .initialQuantity(10)
                            .lmmAllocationPercentage(p)
                            .build();
                }).toList();

        bids.forEach(lmmOrderBook::addOrder);

        final Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(lmm)
                .buy(false)
                .price(100L)
                .initialQuantity(1)
                .build();

        lmmOrderBook.addOrder(ask);
        final OrderUpdate response = lmmOrderBook.getLastOrderUpdate(ask.getId());

        final List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L)
        );

        final List<MatchEvent> matches = response.getMatches();
        final boolean success = matches.size() == expectedMatches.size()
                && IntStream.range(0, matches.size())
                .noneMatch(i -> matches.get(i).getRestingOrderId() != expectedMatches.get(i).getRestingOrderId()
                        || matches.get(i).getMatchQuantity() != expectedMatches.get(i).getMatchQuantity());

        if (!success) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testLMMWithTopOrderBookMultipleAggressors() {
        lmmTopOrderBook.clear();

        final List<Order> bids = Stream.of(0, 20, 80)
                .map(p -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(lmmTop)
                            .buy(true)
                            .price(100L)
                            .initialQuantity(100)
                            .lmmAllocationPercentage(p)
                            .build();
                }).toList();

        bids.forEach(o -> {
            lmmTopOrderBook.addOrder(o);
        });

        Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(lmmTop)
                .buy(false)
                .price(100L)
                .initialQuantity(30)
                .build();

        lmmTopOrderBook.addOrder(ask);
        OrderUpdate response = lmmTopOrderBook.getLastOrderUpdate(ask.getId());

        // Top order should snag all the quantity of the aggressor leaving none for the LMMs
        List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 30, false, 0L)
        );
        List<MatchEvent> matches = response.getMatches();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 1", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }

        // Since top order was not completely filled, it should still be top
        List<Order> top = bids.stream().filter(Order::isTop).toList();
        final String topOrderId = "Order id: " + top.stream()
                .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

        if (top.size() != 1) {
            fail(getFailMessage("Not exactly 1 top order"));
        }

        if (!bids.get(0).isTop()) {
            fail(getFailMessage("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId)));
        }

        /*
         * Since top order was not completely filled (70 lots left), it should
         * still be considered top and snag all the quantity from the next aggressor
         */
        ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(lmmTop)
                .buy(false)
                .price(100L)
                .initialQuantity(30)
                .build();

        lmmTopOrderBook.addOrder(ask);
        response = lmmTopOrderBook.getLastOrderUpdate(ask.getId());

        expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 30, false, 0L)
        );
        matches = response.getMatches();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 2", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testLMMOrderBookMultipleAggressors() {
        lmmOrderBook.clear();

        final List<Order> bids = Stream.of(0, 20, 80)
                .map(p -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(lmm)
                            .buy(true)
                            .price(100L)
                            .initialQuantity(100)
                            .lmmAllocationPercentage(p)
                            .build();
                }).toList();

        bids.forEach(lmmOrderBook::addOrder);

        Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(lmm)
                .buy(false)
                .price(100L)
                .initialQuantity(30)
                .build();

        lmmOrderBook.addOrder(ask);
        OrderUpdate response = lmmOrderBook.getLastOrderUpdate(ask.getId());

        List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 6, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 24, false, 0L)
        );
        List<MatchEvent> matches = response.getMatches();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 1", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }

        ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(lmm)
                .buy(false)
                .price(100L)
                .initialQuantity(30)
                .build();

        lmmOrderBook.addOrder(ask);
        response = lmmOrderBook.getLastOrderUpdate(ask.getId());

        expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 6, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 24, false, 0L)
        );
        matches = response.getMatches();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches 2", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testLMMOrderBook() {
        lmmOrderBook.clear();

        final List<Order> bids = Stream.of(0, 20, 80)
                .map(p -> {
                    hold(10);
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(lmm)
                            .buy(true)
                            .price(100L)
                            .initialQuantity(10)
                            .lmmAllocationPercentage(p)
                            .build();
                }).toList();

        bids.forEach(lmmOrderBook::addOrder);

        final Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(lmm)
                .buy(false)
                .price(100L)
                .initialQuantity(30)
                .build();

        lmmOrderBook.addOrder(ask);
        final OrderUpdate response = lmmOrderBook.getLastOrderUpdate(ask.getId());

        final List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 6, false, 0L),
                new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 10, false, 0L),
                new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 10, false, 0L),
                new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 4, false, 0L)
        );
        final List<MatchEvent> matches = response.getMatches();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testFIFOOrderBook() {
        fifoOrderBook.clear();
        // 5 bid price levels, with the 200 price level having multiple orders (to test time priority)
        final List<Long> bidPrices = List.of(100L, 150L, 200L, 200L, 250L, 300L);
        final List<Long> askPrices = List.of(100L, 150L, 200L, 250L, 300L);

        final List<Order> bids = bidPrices.stream()
                .map(p -> {
                    hold(10); // So that each order has slightly different timestamps
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(fifo)
                            .buy(true)
                            .price(p)
                            .initialQuantity(10)
                            .build();
                }).toList();

        final List<Order> asks = askPrices.stream()
                .map(p -> {
                    hold(10); // So that each order has slightly different timestamps
                    return Order.builder().clientOrderId(Integer.toString(0))
                            .security(fifo)
                            .buy(false)
                            .price(p)
                            .initialQuantity(10)
                            .build();
                }).toList();

        // Add bids
        bids.forEach(fifoOrderBook::addOrder);
        asks.forEach(fifoOrderBook::addOrder);

        final List<OrderUpdate> responses = asks.stream().map(Order::getId).map(fifoOrderBook::getLastOrderUpdate).toList();

        final List<MatchEvent> matches = responses.stream()
                .filter(u -> !u.isEmpty())
                .map(r -> r.getMatches().get(0))
                .toList();

        final List<MatchEvent> expectedMatches = List.of(
                new MatchEvent(asks.get(0).getId(), bids.get(5).getId(), 300L, 10, false, 0L),
                new MatchEvent(asks.get(1).getId(), bids.get(4).getId(), 250L, 10, false, 0L),
                new MatchEvent(asks.get(2).getId(), bids.get(2).getId(), 200L, 10, false, 0L)
        );

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }

        /*
         * Resulting order book state should be bids: {200, 150, 100}, asks: {250, 300}
         */
        final List<Long> expectedBids = List.of(200L, 150L, 100L);
        final List<Long> expectedAsks = List.of(250L, 300L);
        final List<Long> actualBids = fifoOrderBook.getBidPrices();
        final List<Long> actualAsks = fifoOrderBook.getAskPrices();

        if (!(actualBids.size() == expectedBids.size() && actualAsks.size() == expectedAsks.size())) {
            fail(getFailMessage("bids", expectedBids.stream().map(Object::toString).toList(), actualBids.stream().map(Object::toString).toList()));
            fail(getFailMessage("asks", expectedAsks.stream().map(Object::toString).toList(), actualAsks.stream().map(Object::toString).toList()));

        }

        if (IntStream.range(0, expectedBids.size()).anyMatch(i -> expectedBids.get(i) != actualBids.get(i).longValue())) {
            fail(getFailMessage("bids", expectedBids.stream().map(Object::toString).toList(), actualBids.stream().map(Object::toString).toList()));
            fail(getFailMessage("asks", expectedAsks.stream().map(Object::toString).toList(), actualAsks.stream().map(Object::toString).toList()));
        }

        if (IntStream.range(0, expectedAsks.size()).anyMatch(i -> expectedAsks.get(i) != actualAsks.get(i).longValue())) {
            fail(getFailMessage("bids", expectedBids.stream().map(Object::toString).toList(), actualBids.stream().map(Object::toString).toList()));
            fail(getFailMessage("asks", expectedAsks.stream().map(Object::toString).toList(), actualAsks.stream().map(Object::toString).toList()));
        }
    }
}
