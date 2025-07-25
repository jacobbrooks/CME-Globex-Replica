package com.cme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class OrderQualifiersTest extends OrderBookTest {

    private final Security fifo = Security.builder().id(1)
            .matchingAlgorithm(MatchingAlgorithm.FIFO)
            .build();

    private final TradingEngine engine = new TradingEngine();
    private final OrderBook fifoOrderBook = new OrderBook(fifo, engine);

    @Test
    public void testIcebergOrder() {
        fifoOrderBook.clear();
        engine.addOrderBook(fifoOrderBook);
        engine.start();

        // Create resting limit orders
        final List<Order> asks = Stream.of(200L, 300L)
                .map(price -> Order.builder().clientOrderId(Integer.toString(0))
                        .security(fifo).buy(false).price(price).initialQuantity(2)
                        .build()).toList();

        // Create iceberg bid with display quantity 1
        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(300L).initialQuantity(4).displayQuantity(1)
                .build();

        asks.forEach(fifoOrderBook::addOrder);
        fifoOrderBook.addOrder(bid);

        // Wait a little to make sure all slices are matched (happens in another thread)
        hold(10);

        final List<MatchEvent> matches = asks.stream()
                .flatMap(a -> fifoOrderBook.getOrderUpdates(a.getId()).stream()
                        .filter(u -> !u.isEmpty())
                        .flatMap(u -> u.getMatches().stream())).toList();

        // The id of each iceberg child/slice should be consecutive +1 increments of the parent iceberg
        final List<MatchEvent> expectedMatches = List.of(new MatchEvent(bid.getId() + 1, asks.get(0).getId(), 200L, 1, true, 0L),
                new MatchEvent(bid.getId() + 2, asks.get(0).getId(), 200L, 1, true, 0L),
                new MatchEvent(bid.getId() + 3, asks.get(1).getId(), 300L, 1, true, 0L),
                new MatchEvent(bid.getId() + 4, asks.get(1).getId(), 300L, 1, true, 0L));

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    /*
     * This also in effect tests FOK orders because minQty = initialQty
     */
    @Test
    public void testFAKWithMinQuantityMet() {
        fifoOrderBook.clear();

        // Create resting limit orders
        final List<Order> asks = Stream.of(200L, 300L)
                .map(price -> Order.builder().clientOrderId(Integer.toString(0))
                        .security(fifo).buy(false).price(price).initialQuantity(1)
                        .build()).toList();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(300L).initialQuantity(2).minQuantity(2)
                .timeInForce(TimeInForce.FAK).build();

        asks.forEach(fifoOrderBook::addOrder);

        // Order should hit 200, 300 price levels and be left with 1 lot remaining, which will
        // be ignored due to the order expiring
        fifoOrderBook.addOrder(bid);

        assertSame(OrderStatus.New, fifoOrderBook.getOrderUpdates(bid.getId()).get(0).getStatus());
        assertSame(OrderStatus.PartialFill, fifoOrderBook.getOrderUpdates(bid.getId()).get(1).getStatus());
        assertSame(OrderStatus.CompleteFill, fifoOrderBook.getOrderUpdates(bid.getId()).get(2).getStatus());

        // Remaining qty of the bid should not rest on the book
        assertTrue(fifoOrderBook.isEmpty());

        final List<MatchEvent> matches = fifoOrderBook.getOrderUpdates(bid.getId()).stream()
                .filter(e -> !e.isEmpty()).map(e -> e.getMatches().get(0))
                .toList();

        final List<MatchEvent> expectedMatches = List.of(new MatchEvent(bid.getId(), asks.get(0).getId(), 200L, 1, true, 0L),
                new MatchEvent(bid.getId(), asks.get(1).getId(), 300L, 1, true, 0L));

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    /*
     * This also in effect tests FOK orders because minQty = initialQty
     */
    @Test
    public void testFAKWithMinQuantityNotMet() {
        fifoOrderBook.clear();

        // Create resting limit orders
        final List<Order> asks = Stream.of(200L, 300L)
                .map(price -> Order.builder().clientOrderId(Integer.toString(0))
                        .security(fifo).buy(false).price(price).initialQuantity(1)
                        .build()).toList();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(300L).initialQuantity(3).minQuantity(3)
                .timeInForce(TimeInForce.FAK).build();

        asks.forEach(fifoOrderBook::addOrder);

        // The summed qty of the 200 & 300 price levels does not meet the bid's min qty
        fifoOrderBook.addOrder(bid);

        assertSame(OrderStatus.New, fifoOrderBook.getOrderUpdates(bid.getId()).get(0).getStatus());
        assertSame(OrderStatus.Expired, fifoOrderBook.getOrderUpdates(bid.getId()).get(1).getStatus());

        // Remaining qty of the bid should not rest on the book
        assertTrue(fifoOrderBook.getBidPrices().isEmpty());
    }

    @Test
    public void testFAKOrderNoMin() {
        fifoOrderBook.clear();

        // Create resting limit orders
        final List<Order> asks = Stream.of(200L, 300L)
                .map(price -> Order.builder().clientOrderId(Integer.toString(0))
                        .security(fifo).buy(false).price(price).initialQuantity(1)
                        .build()).toList();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(300L).initialQuantity(3).timeInForce(TimeInForce.FAK).build();

        asks.forEach(fifoOrderBook::addOrder);

        // Order should hit 200, 300 price levels and be left with 1 lot remaining, which will
        // be ignored due to the order expiring
        fifoOrderBook.addOrder(bid);

        assertSame(OrderStatus.New, fifoOrderBook.getOrderUpdates(bid.getId()).get(0).getStatus());
        assertSame(OrderStatus.PartialFill, fifoOrderBook.getOrderUpdates(bid.getId()).get(1).getStatus());
        assertSame(OrderStatus.PartialFill, fifoOrderBook.getOrderUpdates(bid.getId()).get(2).getStatus());
        assertSame(OrderStatus.Expired, fifoOrderBook.getOrderUpdates(bid.getId()).get(3).getStatus());

        // Remaining qty of the bid should not rest on the book
        assertTrue(fifoOrderBook.isEmpty());

        final List<MatchEvent> matches = fifoOrderBook.getOrderUpdates(bid.getId()).stream()
                .filter(e -> !e.isEmpty()).map(e -> e.getMatches().get(0))
                .toList();

        final List<MatchEvent> expectedMatches = List.of(new MatchEvent(bid.getId(), asks.get(0).getId(), 200L, 1, true, 0L),
                new MatchEvent(bid.getId(), asks.get(1).getId(), 300L, 1, true, 0L));

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

}
