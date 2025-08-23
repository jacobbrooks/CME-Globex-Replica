package com.cme;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class OrderFunctionalitiesTest extends OrderBookTest {

    /*
     * We are testing on an OrderBook for a security that uses the Configurable matching
     * algorithm specifically because it is a complex matching algorithm with many steps. We
     * want to prove that the internal state of the book & prioritization of the orders
     * remain intact after invasive/unnatural actions like order cancels & modifications.
     */
    private final Security configurable = Security.builder().id(1)
            .matchingAlgorithm(MatchingAlgorithm.Configurable)
            .proRataMin(2).splitPercentage(40).build();

    private final Security fifo = Security.builder().id(2)
            .matchingAlgorithm(MatchingAlgorithm.FIFO)
            .build();

    private final TradingEngine engine = new TradingEngine();
    private final OrderBook fifoOrderBook = new OrderBook(fifo, engine, engine.getOrderUpdateService());
    private final OrderBook orderBook = new OrderBook(configurable, new TradingEngine(), engine.getOrderUpdateService());

    public OrderFunctionalitiesTest() {
        engine.addOrderBook(fifoOrderBook);
        engine.start();
    }

    @Test
    public void testModifyStopOrderTriggerPrice() {
        fifoOrderBook.clear();

        final Order stopOrder = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).price(200L).initialQuantity(10).orderType(OrderType.StopLimit)
                .triggerPrice(100L).build();

        final Order resting = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).price(75L).initialQuantity(1)
                .build();

        final Order aggressing = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(false).price(75L).initialQuantity(1)
                .build();

        final Order stopOrderMatch = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(false).price(200L).initialQuantity(10).orderType(OrderType.Limit)
                .build();

        // Stop order should be accepted and wait in the stop order queue
        engine.submit(stopOrder);

        engine.waitForOrderBell(stopOrder.getId());

        assertFalse(Optional.ofNullable(fifoOrderBook.getOrderUpdates(stopOrder.getId())).orElse(Collections.emptyList()).isEmpty());
        assertSame(OrderType.StopLimit, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(0).getAggressingOrderType());
        assertSame(OrderStatus.New, fifoOrderBook.getOrderUpdates(stopOrder.getId()).get(0).getStatus());

        // A limit order at the stop order's trigger price which just rests on the book should not yet trigger the stop order
        engine.submit(resting);
        engine.waitForOrderBell(resting.getId());
        assertFalse(Optional.ofNullable(fifoOrderBook.getOrderUpdates(stopOrder.getId())).orElse(Collections.emptyList()).size() > 1);

        // Trigger price was originally 100, so a trade at 75 would not originally trigger the stop order.
        // Now we are modifying the trigger price to 75, so we will see if a trade at 75 properly triggers it.
        final OrderModify orderModify = OrderModify.builder().orderId(stopOrder.getId()).triggerPrice(75L).build();
        engine.modify(orderModify);
        engine.waitForOrderBell(stopOrder.getId() + 4);

        // Aggressing limit order should match against the resting limit order, which then should trigger the stop order
        engine.submit(aggressing);

        engine.waitForOrderBell(aggressing.getId());

        assertSame(OrderType.Limit, fifoOrderBook.getLastOrderUpdate(stopOrder.getId() + 4).getAggressingOrderType());
        assertFalse(fifoOrderBook.isEmpty());

        engine.submit(stopOrderMatch);
        engine.waitForOrderBell(stopOrderMatch.getId());
        // Stop order fill notice should be 3rd update
        assertSame(OrderType.Limit, fifoOrderBook.getLastOrderUpdate(stopOrder.getId() + 4).getAggressingOrderType());
        assertSame(OrderStatus.CompleteFill, fifoOrderBook.getLastOrderUpdate(stopOrder.getId() + 4).getStatus());

        final List<MatchEvent> expectedMatches = List.of(new MatchEvent(stopOrderMatch.getId(), stopOrder.getId() + 4, 200L, 10, false, 0L));
        final List<MatchEvent> matches = fifoOrderBook.getLastOrderUpdate(stopOrderMatch.getId()).getMatches();

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }
    }

    @Test
    public void testOrderModifyRejectAfterAlreadyFilled() {
        fifoOrderBook.clear();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(10)
                .build();

        final Order ask = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(false).price(100L).initialQuantity(10)
                .build();

        engine.submit(bid);

        engine.waitForOrderBell(bid.getId());

        // Place the original bid on hold so that we can fully match it before the modify is processed
        engine.placeOnProcessHold(bid.getId());

        final OrderModify orderModify = OrderModify.builder().orderId(bid.getId())
                .quantity(5).build();
        engine.modify(orderModify);

        engine.submit(ask);

        engine.waitForOrderBell(ask.getId());

        engine.removeProcessHold(bid.getId());

        // Wait for replacement order to be processed (shouldn't be submitted due to IFM)
        engine.waitForOrderBell(bid.getId());

        assertSame(OrderStatus.Reject, orderBook.getLastOrderUpdate(bid.getId()).getStatus());
        assertTrue(orderBook.isEmpty());
    }

    @Test
    public void testIcebergOrderInFlightMitigationNegativeInterceptedMatch() {
        fifoOrderBook.clear();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(6).displayQuantity(2)
                .build();

        final Order ask = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(false).price(100L).initialQuantity(4).build();

        engine.submit(bid);

        engine.waitForOrderBell(bid.getId() + 2);

        // Put the order modify on hold to simulate the ask matching against the resting slice after
        // the modify has already been submitted, but before it has actually been processed i.e.
        // The resting order quantity changed while the modify request was "in flight"
        engine.placeOnProcessHold(bid.getId());

        final OrderModify orderModify = OrderModify.builder()
                .orderId(bid.getId()).price(100L).quantity(1).inFlightMitigation(true)
                .build();
        engine.modify(orderModify);

        // The ask should match against the initial bid slice for 2, then the remaining ask qty of 2
        // should go to rest on the book. The match against the bid slice should trigger the release
        // of another slice, which we are putting on hold to prevent it from entering the book, allowing
        // us to simulate the order modification executing before processing the 2nd slice
        engine.placeOnProcessHold(bid.getId() + 3);
        engine.submit(ask);

        engine.waitForOrderBell(ask.getId());

        // The modification qty is now less than what we already matched for, so removing the
        // modify hold and allowing  request to finally process will result in a cancel
        // without replacement, hopefully cancelling the unprocessed released slice before it can
        // enter the book
        engine.removeProcessHold(bid.getId());

        // "Allow" the released slice to enter the book but hope it doesn't actually, because the modify
        // request should have cancelled it while it was in purgatory
        engine.removeProcessHold(bid.getId() + 3);
        engine.waitForOrderBell(bid.getId());

        assertSame(OrderStatus.Cancelled, fifoOrderBook.getLastOrderUpdate(bid.getId()).getStatus());
        assertTrue(fifoOrderBook.hasOrder(ask.getId()));
        assertEquals(2, ask.getRemainingQuantity());
    }

    @Test
    public void testIcebergOrderInFlightMitigationPositive() {
        fifoOrderBook.clear();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(8).displayQuantity(2)
                .build();

        final Order ask = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(false).price(100L).initialQuantity(4).build();

        engine.submit(bid);

        engine.waitForOrderBell(bid.getId() + 2);

        engine.placeOnProcessHold(bid.getId());

        final OrderModify orderModify = OrderModify.builder()
                .orderId(bid.getId()).price(100L).quantity(6).inFlightMitigation(true)
                .build();
        engine.modify(orderModify);

        // The ask should match against the initial bid slice for 2, then the remaining ask qty of 2
        // should go to rest on the book. The match against the bid slice should trigger the release
        // of another slice which will match against the now resting ask for another 2 lots. Finally,
        // the final slice should be released and rest on the book.
        engine.submit(ask);

        engine.waitForOrderBell(bid.getId() + 4);

        // The modify quantity is greater than what we already matched for, so removing the
        // hold and allowing the modify request to finally process will result in a cancel
        // with a replacement order quantity of 2 (modification qty 6 - filled qty 4)
        engine.removeProcessHold(bid.getId());

        engine.waitForOrderBell(bid.getId() + 6);

        assertSame(OrderStatus.Cancelled, fifoOrderBook.getLastOrderUpdate(bid.getId()).getStatus());
        assertSame(OrderStatus.Cancelled, fifoOrderBook.getLastOrderUpdate(bid.getId() + 4).getStatus());
        assertSame(OrderStatus.New, fifoOrderBook.getLastOrderUpdate(bid.getId() + 6).getStatus());
        assertEquals(2, fifoOrderBook.getOrders().get(bid.getId() + 6).getRemainingQuantity());
        assertFalse(fifoOrderBook.isEmpty());
    }

    @Test
    public void testIcebergOrderInFlightMitigationNegative() {
        fifoOrderBook.clear();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(6).displayQuantity(2)
                .build();

        final Order ask = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(false).price(100L).initialQuantity(4).build();

        engine.submit(bid);

        engine.waitForOrderBell(bid.getId() + 2);

        engine.placeOnProcessHold(bid.getId());

        final OrderModify orderModify = OrderModify.builder()
                .orderId(bid.getId()).price(100L).quantity(2).inFlightMitigation(true)
                .build();
        engine.modify(orderModify);

        // The ask should match against the initial bid slice for 2, then the remaining ask qty of 2
        // should go to rest on the book. The match against the bid slice should trigger the release
        // of another slice which will match against the now resting ask for another 2 lots. Finally,
        // the final slice should be released and rest on the book.
        engine.submit(ask);

        engine.waitForOrderBell(bid.getId() + 4);

        // The modify quantity is now less than what we already matched for, so removing the
        // iceberg hold and allowing the modify request to finally process will result in a cancel
        // without replacement
        engine.removeProcessHold(bid.getId());

        engine.waitForOrderBell(bid.getId() + 4);

        assertSame(OrderStatus.Cancelled, fifoOrderBook.getLastOrderUpdate(bid.getId()).getStatus());
        assertSame(OrderStatus.Cancelled, fifoOrderBook.getLastOrderUpdate(bid.getId() + 4).getStatus());
        assertTrue(fifoOrderBook.isEmpty());
    }

    @Test
    public void testBasicInFlightMitigationNegative() {
        fifoOrderBook.clear();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(10)
                .build();

        final Order ask = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(false).price(100L).initialQuantity(6)
                .build();

        engine.submit(bid);

        engine.waitForOrderBell(bid.getId());

        // Place the original bid on hold so that we can match against it before the cancel is processed,
        // allowing us to test if the IFM takes effect
        engine.placeOnProcessHold(bid.getId());

        final OrderModify orderModify = OrderModify.builder().orderId(bid.getId())
                .quantity(5).inFlightMitigation(true).build();
        engine.modify(orderModify);

        engine.submit(ask);

        engine.waitForOrderBell(ask.getId());

        engine.removeProcessHold(bid.getId());

        // Wait for replacement order to be processed (shouldn't be submitted due to IFM)
        engine.waitForOrderBell(bid.getId());

        assertSame(OrderStatus.Cancelled, fifoOrderBook.getLastOrderUpdate(bid.getId()).getStatus());
        assertTrue(orderBook.isEmpty());
    }

    @Test
    public void testBasicInFlightMitigationPositive() {
        fifoOrderBook.clear();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(5)
                .build();

        final Order ask = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(false).price(100L).initialQuantity(4)
                .build();

        engine.submit(bid);

        engine.waitForOrderBell(bid.getId());

        // Place the original bid on hold so that we can match against it before the cancel is processed,
        // allowing us to test if the IFM takes effect
        engine.placeOnProcessHold(bid.getId());

        final OrderModify orderModify = OrderModify.builder().orderId(bid.getId())
                .quantity(10).inFlightMitigation(true).build();
        engine.modify(orderModify);

        engine.submit(ask);

        engine.waitForOrderBell(ask.getId());

        engine.removeProcessHold(bid.getId());

        // Wait for replacement order to be processed
        engine.waitForOrderBell(bid.getId() + 2);

        assertSame(OrderStatus.Cancelled, fifoOrderBook.getLastOrderUpdate(bid.getId()).getStatus());

        final Optional<Order> replacement = fifoOrderBook.getOrders().values().stream()
                .filter(order -> order.getOriginId() == bid.getId()).findAny();

        replacement.ifPresentOrElse(o -> {
            assertEquals(o.getInitialQuantity(), orderModify.getQuantity() - ask.getInitialQuantity());
        }, () -> {
            fail("Replacement order not present in order book.");
        });
    }

    @Test
    public void testIcebergOrderModify() {
        fifoOrderBook.clear();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(10).displayQuantity(2)
                .build();

        engine.submit(bid);

        engine.waitForOrderBell(bid.getId() + 1);

        assertFalse(fifoOrderBook.isEmpty());
        assertTrue(fifoOrderBook.getOrders().containsKey(bid.getId() + 1));
        assertTrue(fifoOrderBook.getOrders().get(bid.getId() + 1).isSlice());
        assertSame(OrderStatus.New, fifoOrderBook.getLastOrderUpdate(bid.getId() + 1).getStatus());
        assertEquals(2, fifoOrderBook.getOrders().get(bid.getId() + 1).getRemainingQuantity());

        final OrderModify orderModify = OrderModify.builder().orderId(bid.getId()).price(200L).displayQuantity(1).build();

        engine.modify(orderModify);

        engine.waitForOrderBell(bid.getId() + 3);

        assertSame(OrderStatus.Cancelled, fifoOrderBook.getLastOrderUpdate(bid.getId()).getStatus());

        assertFalse(fifoOrderBook.isEmpty());
        assertTrue(fifoOrderBook.getOrders().containsKey(bid.getId() + 3));
        assertTrue(fifoOrderBook.getOrders().get(bid.getId() + 3).isSlice());
        assertSame(OrderStatus.New, fifoOrderBook.getLastOrderUpdate(bid.getId() + 3).getStatus());
        assertEquals(1, fifoOrderBook.getOrders().get(bid.getId() + 3).getRemainingQuantity());
    }

    @Test
    public void testBasicOrderModify() {
        fifoOrderBook.clear();

        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(100L).initialQuantity(1)
                .build();

        engine.submit(bid);

        engine.waitForOrderBell(bid.getId());

        final OrderModify orderModify = OrderModify.builder().orderId(bid.getId()).price(200L).quantity(2).build();

        engine.modify(orderModify);

        engine.waitForOrderBell(bid.getId() + 1);

        assertSame(OrderStatus.Cancelled, fifoOrderBook.getLastOrderUpdate(bid.getId()).getStatus());
        assertFalse(fifoOrderBook.isEmpty());

        final Optional<Order> replacement = fifoOrderBook.getOrders().values().stream()
                .filter(order -> order.getOriginId() == bid.getId()).findAny();

        replacement.ifPresentOrElse(o -> {
            assertEquals(o.getPrice(), orderModify.getPrice());
            assertEquals(o.getInitialQuantity(), orderModify.getQuantity());
        }, () -> {
            fail("Replacement order not present in order book.");
        });
    }

    @Test
    public void testCancelIcebergOrder() {
        fifoOrderBook.clear();

        // Create resting limit orders
        final List<Order> asks = Stream.of(new int[]{200, 2}, new int[]{300, 1})
                .map(priceQty -> Order.builder().clientOrderId(Integer.toString(0))
                        .security(fifo).buy(false).price((long) priceQty[0]).initialQuantity(priceQty[1])
                        .build()).toList();

        // Create iceberg bid with display quantity 1
        final Order bid = Order.builder().clientOrderId(Integer.toString(0)).security(fifo)
                .buy(true).price(300L).initialQuantity(4).displayQuantity(1)
                .build();

        asks.forEach(engine::submit);

        engine.waitForOrderBell(asks.get(asks.size() - 1).getId());

        engine.submit(bid);

        engine.waitForOrderBell(bid.getId() + 4);

        final List<MatchEvent> matches = asks.stream()
                .flatMap(a -> fifoOrderBook.getOrderUpdates(a.getId()).stream()
                        .filter(u -> !u.isEmpty())
                        .flatMap(u -> u.getMatches().stream())).toList();

        // The id of each iceberg child/slice should be consecutive +1 increments of the parent iceberg
        final List<MatchEvent> expectedMatches = List.of(new MatchEvent(bid.getId() + 1, asks.get(0).getId(), 200L, 1, true, 0L),
                new MatchEvent(bid.getId() + 2, asks.get(0).getId(), 200L, 1, true, 0L),
                new MatchEvent(bid.getId() + 3, asks.get(1).getId(), 300L, 1, true, 0L));

        if (!equalMatches(expectedMatches, matches)) {
            fail(getFailMessage("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList()));
        }

        // Since the aggressing bid had 4 lots and there were only 3 lots resting, there should be a slice left sitting on the book
        assertTrue(fifoOrderBook.getOrders().containsKey(bid.getId() + 4) && !fifoOrderBook.isEmpty());

        engine.cancel(bid.getId());

        engine.waitForOrderBell(bid.getId());

        assertTrue(fifoOrderBook.isEmpty());
        assertFalse(fifoOrderBook.getOrders().containsKey(bid.getId()));
        assertFalse(fifoOrderBook.getOrders().containsKey(bid.getId() + 4));
    }

    @Test
    public void testCancelTOPOrder() {
        orderBook.clear();
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

        bids.forEach(orderBook::addOrder);

        // Cancel TOP order
        orderBook.cancelOrder(bids.get(0).getId(), false);

        assertTrue(Optional.ofNullable(orderBook.getTopBid().get()).isEmpty());

        Order ask = Order.builder().clientOrderId(Integer.toString(0))
                .security(configurable)
                .buy(false)
                .price(100L)
                .initialQuantity(200)
                .lmmAllocationPercentage(0)
                .build();

        orderBook.addOrder(ask);
        OrderUpdate response = orderBook.getLastOrderUpdate(ask.getId());

        /*
         * 1. LMM pass - 10% LMM order should receive 10% * 200 = 20 lots -> aggressor qty = 180
         * 2. LMM pass - 20% LMM order should receive 20% * 200 = 40 lots -> aggressor qty = 140
         * 3. Split FIFO pass - 40% of the aggressor qty (40% * 140 = 56) should go to the earliest
         *       order in the book (order 1) which only has 31 lots available -> aggressor qty = 109,
         *       remaining splitFIFO qty = 25
         * 4. Split FIFO pass - 7 of 25 Remaining lots are assigned to the next earliest (order 2)
         *       -> aggressor qty = 102, remaining splitFIFO qty = 18
         * 5. Split FIFO pass - 18 remaining lots are assigned to the next earliest (order 3)
         *       -> aggressor qty = 84
         * 6. Pro Rata pass - Total resting qty on the book is 86 lots, order 3 has the biggest
         *       proportion with 82 lots, and is filled for 84 * (82 / 86) = 80 lots
         *       -> aggressor qty = 4
         * 7. Pro Rata pass - orders 4,5,6 & 7 all have the next biggest proportions, each with 1 lot,
         *       84 * (1 / 86) = 0.9767 lots which is rounded down to 0, so they are all marked for leveling
         * 8. Leveling pass - Order 4,5,6 & 7 are assigned 1 lot in that order due to qty/time priority
         *       -> aggressor qty = 0
         */
        List<MatchEvent> matches = response.getMatches();
        List<MatchEvent> expectedMatches = List.of(
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
    }

    @Test
    public void testCancelOrder() {
        orderBook.clear();
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
        orderBook.cancelOrder(bids.get(1).getId(), false);

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
    }

}
