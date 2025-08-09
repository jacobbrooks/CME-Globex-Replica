package com.cme;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class OrderQualifiersTest extends OrderBookTest {

    private final Security fifo = Security.builder().id(1)
            .matchingAlgorithm(MatchingAlgorithm.FIFO)
            .build();

    private final TradingEngine engine = new TradingEngine();
    private final OrderBook fifoOrderBook = new OrderBook(fifo, engine);

    public OrderQualifiersTest() {
        engine.addOrderBook(fifoOrderBook);
        engine.start();
    }

    @Test
    public void testGTCOrderExpirationDueToSecurityExpiration() {
        // We need local objects for this test with an unstarted engine to have control over time mocking
        final Security fifoExpiringSoon = Security.builder().id(2)
                .matchingAlgorithm(MatchingAlgorithm.FIFO)
                .expiration(LocalDate.now())
                .build();

        final TradingEngine localEngine = new TradingEngine();
        final OrderBook localOrderBook = new OrderBook(fifoExpiringSoon, localEngine);
        localEngine.addOrderBook(localOrderBook);

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifoExpiringSoon)
                .buy(true).price(100L).initialQuantity(1).timeInForce(TimeInForce.GTC).expiration(LocalDate.now())
                .build();

        // See if the security's expiration forces the indefinite GTC order to expire
        localEngine.submit(bid);
        localEngine.setNextExpirationTime(ZonedDateTime.now().plusSeconds(2));
        localEngine.start();

        hold(10);

        assertFalse(localEngine.getOrderBooksByOrderId().isEmpty());
        assertFalse(localOrderBook.isEmpty());

        hold(2000);

        assertTrue(localEngine.getOrderBooksByOrderId().isEmpty());
        assertTrue(localOrderBook.isEmpty());
        assertSame(OrderStatus.Expired, localOrderBook.getLastOrderUpdate(bid.getId()).getStatus());
    }

    @Test
    public void testGTDOrderExpiration() {
        // We need local objects for this test with an unstarted engine to have control over time mocking
        final TradingEngine localEngine = new TradingEngine();
        final OrderBook localOrderBook = new OrderBook(fifo, localEngine);
        localEngine.addOrderBook(localOrderBook);

        final Order bidToExpire = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(1).timeInForce(TimeInForce.GTD).expiration(LocalDate.now())
                .build();

        final Order bidToRemain = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(1).timeInForce(TimeInForce.GTD).expiration(LocalDate.now().plusDays(1))
                .build();

        // Set the end of the trading day to 2 seconds from now and see if the engine's internal scheduler correctly cancels
        // the GTD bid which expires today
        localEngine.submit(bidToExpire);
        localEngine.submit(bidToRemain);
        localEngine.setNextExpirationTime(ZonedDateTime.now().plusSeconds(2));
        localEngine.start();

        hold(10);

        assertFalse(localEngine.getOrderBooksByOrderId().isEmpty());
        assertFalse(localOrderBook.isEmpty());

        hold(2000);

        assertEquals(1, localEngine.getOrderBooksByOrderId().size());
        assertEquals(1, localOrderBook.getOrders().size());
        assertSame(OrderStatus.Expired, localOrderBook.getLastOrderUpdate(bidToExpire.getId()).getStatus());
        assertSame(OrderStatus.New, localOrderBook.getLastOrderUpdate(bidToRemain.getId()).getStatus());
    }

    @Test
    public void testDayOrderExpiration() {
        // We need local objects for this test with an unstarted engine to have control over time mocking
        final TradingEngine localEngine = new TradingEngine();
        final OrderBook localOrderBook = new OrderBook(fifo, localEngine);
        localEngine.addOrderBook(localOrderBook);

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(1).timeInForce(TimeInForce.Day)
                .build();

        // Set the end of the trading day to 2 seconds from now and see if the engine's internal scheduler correctly cancels the bid
        localEngine.submit(bid);
        localEngine.setNextExpirationTime(ZonedDateTime.now().plusSeconds(2));
        localEngine.start();

        hold(10);

        assertFalse(localEngine.getOrderBooksByOrderId().isEmpty());
        assertFalse(localOrderBook.isEmpty());

        hold(2000);

        assertTrue(localEngine.getOrderBooksByOrderId().isEmpty());
        assertTrue(localOrderBook.isEmpty());
        assertSame(OrderStatus.Expired, localOrderBook.getLastOrderUpdate(bid.getId()).getStatus());
    }

    @Test
    public void testIcebergOrder() {
        fifoOrderBook.clear();

        // Create resting limit orders
        final List<Order> asks = Stream.of(200L, 300L)
                .map(price -> Order.builder().clientOrderId(Integer.toString(0))
                        .security(fifo).buy(false).price(price).initialQuantity(2)
                        .build()).toList();

        // Create iceberg bid with display quantity 1
        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(300L).initialQuantity(4).displayQuantity(1)
                .build();

        asks.forEach(engine::submit);

        hold(10);

        engine.submit(bid);

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
