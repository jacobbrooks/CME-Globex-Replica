package com.cme;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class OrderTypesTest extends OrderBookTest {

    private final Security fifo = Security.builder().id(1)
            .matchingAlgorithm(MatchingAlgorithm.FIFO)
            .protectionPoints(100)
            .build();
    private final OrderBook fifoOrderBook = new OrderBook(fifo, null);

    @Test
    public void testMarkerOrderWithProtection() {
        fifoOrderBook.clear();

        // Create resting limit orders
        final List<Order> asks = Stream.of(100L, 150L, 200L, 250L)
                .map(price -> Order.builder().clientOrderId(Integer.toString(0))
                        .security(fifo).buy(false).price(price).initialQuantity(1)
                        .build()).toList();

        asks.forEach(fifoOrderBook::addOrder);

        final Order marketWithProtection = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).initialQuantity(4).orderType(OrderType.MarketWithProtection)
                .build();

        fifoOrderBook.addOrder(marketWithProtection);

        assertSame(OrderType.MarketWithProtection, fifoOrderBook.getOrderUpdates(marketWithProtection.getId()).get(0).getAggressingOrderType());
        assertSame(OrderStatus.New, fifoOrderBook.getOrderUpdates(marketWithProtection.getId()).get(0).getStatus());

        fifoOrderBook.getOrderUpdates(marketWithProtection.getId()).stream()
                .filter(u -> u.getStatus() != OrderStatus.New)
                .forEach(u -> {
                    assertSame(OrderType.MarketWithProtection, u.getAggressingOrderType());
                    assertSame(OrderStatus.PartialFill, u.getStatus());
                });

        // Bid should sweep the book up until the 250 ask because that is out of the protection range
        final List<MatchEvent> expectedMatches = List.of(new MatchEvent(marketWithProtection.getId(), asks.get(0).getId(), 100L, 1, true, 0L),
                new MatchEvent(marketWithProtection.getId(), asks.get(1).getId(), 150L, 1, true, 0L),
                new MatchEvent(marketWithProtection.getId(), asks.get(2).getId(), 200L, 1, true, 0L));
        final List<MatchEvent> matches = fifoOrderBook.getOrderUpdates(marketWithProtection.getId()).stream()
                .filter(u -> !u.isEmpty()).map(u -> u.getMatches().get(0)).toList();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }

        // Remaining quantity should rest on the book as a limit order at price = best price (100) + protection points (100) = 200
        assertEquals(200L, fifoOrderBook.getBidPrices().stream().findFirst().orElse(0L));
        assertEquals(200L, marketWithProtection.getPrice());
        assertSame(OrderType.Limit, marketWithProtection.getOrderType());
    }

    @Test
    public void testMarketLimitOrder() {
        fifoOrderBook.clear();

        // Create resting limit orders
        final List<Order> asks = Stream.of(200L, 300L)
                .map(price -> Order.builder().clientOrderId(Integer.toString(0))
                        .security(fifo).buy(false).price(price).initialQuantity(1)
                        .build()).toList();

        asks.forEach(fifoOrderBook::addOrder);

        final Order marketLimitBid = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).initialQuantity(4).orderType(OrderType.MarketLimit)
                .build();

        fifoOrderBook.addOrder(marketLimitBid);

        assertSame(OrderType.MarketLimit, fifoOrderBook.getLastOrderUpdate(marketLimitBid.getId()).getAggressingOrderType());
        assertSame(OrderStatus.PartialFill, fifoOrderBook.getLastOrderUpdate(marketLimitBid.getId()).getStatus());

        // Bid should just match against best price, then rest should remain on book at that price
        final List<MatchEvent> expectedMatches = List.of(new MatchEvent(marketLimitBid.getId(), asks.get(0).getId(), 200L, 1, true, 0L));
        final List<MatchEvent> matches = fifoOrderBook.getLastOrderUpdate(marketLimitBid.getId()).getMatches();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testStopLimitOrder() {
        fifoOrderBook.clear();

        final Order stopOrder = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).price(200L).initialQuantity(10).orderType(OrderType.StopLimit)
                .triggerPrice(100L).build();

        final Order resting = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).price(100L).initialQuantity(1)
                .build();

        final Order aggressing = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(false).price(100L).initialQuantity(1)
                .build();

        final Order stopOrderMatch = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(false).price(200L).initialQuantity(10).orderType(OrderType.Limit)
                .build();

        // Stop order should be accepted and wait in the stop order queue
        fifoOrderBook.addOrder(stopOrder);

        assertFalse(Optional.ofNullable(fifoOrderBook.getOrderUpdates(stopOrder.getId())).orElse(Collections.emptyList()).isEmpty());
        assertSame(OrderType.StopLimit, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(0).getAggressingOrderType());
        assertSame(OrderStatus.New, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(0).getStatus());

        // A limit order at the stop order's trigger price which just rests on the book should not yet trigger the stop order
        fifoOrderBook.addOrder(resting);
        assertFalse(Optional.ofNullable(fifoOrderBook.getOrderUpdates(stopOrder.getId())).orElse(Collections.emptyList()).size() > 1);

        // Aggressing limit order should match against the resting limit order, which then should trigger the stop order
        fifoOrderBook.addOrder(aggressing);
        assertSame(OrderType.Limit, fifoOrderBook.getLastOrderUpdate(stopOrder.getId()).getAggressingOrderType());
        assertFalse(fifoOrderBook.isEmpty());

        fifoOrderBook.addOrder(stopOrderMatch);
        // Stop order fill notice should be 3rd update
        assertSame(OrderType.Limit, fifoOrderBook.getLastOrderUpdate(stopOrder.getId()).getAggressingOrderType());
        assertSame(OrderStatus.CompleteFill, fifoOrderBook.getLastOrderUpdate(stopOrder.getId()).getStatus());

        final List<MatchEvent> expectedMatches = List.of(new MatchEvent(stopOrderMatch.getId(), stopOrder.getId(), 200L, 10, false, 0L));
        final List<MatchEvent> matches = fifoOrderBook.getLastOrderUpdate(stopOrderMatch.getId()).getMatches();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testStopWithProtectionOrder() {
        fifoOrderBook.clear();

        final Order preStopBid = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).price(150L).initialQuantity(1)
                .build();

        final Order preStopAsk = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(false).price(150L).initialQuantity(1)
                .build();

        final Order stopOrder = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).initialQuantity(10).orderType(OrderType.StopWithProtection)
                .triggerPrice(100L).build();

        final Order bidBelowTrigger = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).price(50L).initialQuantity(1)
                .build();

        final Order askBelowTrigger = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(false).price(50L).initialQuantity(1)
                .build();

        final Order stopTriggerBid = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).price(130L).initialQuantity(1)
                .build();

        final Order stopTriggerAsk = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(false).price(130L).initialQuantity(1)
                .build();

        final List<Order> restingAsksToMatchStop = Stream.of(170, 180, 190)
                .map(p -> Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(false).price(p).initialQuantity(1)
                .build()).toList();

        // Set last traded price to meet the trigger price before adding stop order to prove that stop order won't
        // incorrectly get triggered. Stop order should only get triggered when the last traded
        // price meets the trigger price AFTER the stop order is submitted
        fifoOrderBook.addOrder(preStopAsk);
        fifoOrderBook.addOrder(preStopBid);

        // Stop order should be accepted and wait in the stop order queue
        fifoOrderBook.addOrder(stopOrder);

        assertFalse(Optional.ofNullable(fifoOrderBook.getOrderUpdates(stopOrder.getId())).orElse(Collections.emptyList()).isEmpty());
        // CME specification states that StopWithProtection orders are confirmed as StopLimit
        assertSame(OrderType.StopLimit, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(0).getAggressingOrderType());
        assertSame(OrderStatus.New, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(0).getStatus());

        fifoOrderBook.addOrder(bidBelowTrigger);
        assertFalse(Optional.ofNullable(fifoOrderBook.getOrderUpdates(stopOrder.getId())).orElse(Collections.emptyList()).size() > 1);

        // Aggressing ask order should match against the resting limit bid. It is
        // below the stop order's trigger price, so the stop order isn't triggered
        fifoOrderBook.addOrder(askBelowTrigger);

        // Set up some resting asks for the stop order to match against once triggered;
        restingAsksToMatchStop.forEach(order -> fifoOrderBook.addOrder(order));

        // Trigger the stop order with a trade @ 130
        fifoOrderBook.addOrder(stopTriggerBid);
        fifoOrderBook.addOrder(stopTriggerAsk);

        // Stop order gets re-confirmed as a limit order
        assertSame(OrderType.Limit, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(1).getAggressingOrderType());
        assertSame(OrderStatus.New, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(1).getStatus());

        // Stop order fill notice should be 3rd update
        assertSame(OrderType.Limit, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(2).getAggressingOrderType());
        assertSame(OrderStatus.PartialFill, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(2).getStatus());
        final List<MatchEvent> expectedMatches = List.of(new MatchEvent(stopOrder.getId(), restingAsksToMatchStop.get(0).getId(), 170L, 1, true, 0L),
                new MatchEvent(stopOrder.getId(), restingAsksToMatchStop.get(1).getId(), 180L, 1, true, 0L),
                new MatchEvent(stopOrder.getId(), restingAsksToMatchStop.get(2).getId(), 190L, 1, true, 0L));

        final List<MatchEvent> matches = fifoOrderBook.getOrderUpdates(stopOrder.getId()).stream()
                .filter(o -> !o.isEmpty()).map(o -> o.getMatches().get(0))
                .toList();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }

        final long expectedStopOrderPrice = stopOrder.getTriggerPrice() + stopOrder.getProtectionPoints();
        assertSame(1, fifoOrderBook.getBidPrices().size());
        assertTrue(fifoOrderBook.getBidPrices().stream().anyMatch(p -> p == expectedStopOrderPrice));
        assertEquals(7, stopOrder.getRemainingQuantity());
    }

}
