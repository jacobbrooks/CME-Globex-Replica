package com.cme;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class OrderQualifiersTest extends OrderBookTest {

    private final Security fifo = Security.builder().id(1)
            .matchingAlgorithm(MatchingAlgorithm.FIFO)
            .build();

    private final OrderBook fifoOrderBook = new OrderBook(fifo, null);

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
