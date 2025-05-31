package com.cme;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;


public class OrderTypesTest extends OrderBookTest {

    private final Security fifo = new Security(1, MatchingAlgorithm.FIFO);
    private final OrderBook fifoOrderBook = new OrderBook(fifo);

    @Test
    public void testStopLimitOrder() {
        final Order stopOrder = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).price(200L).initialQuantity(10).orderType(OrderType.Stop)
                .triggerPrice(100L).build();

        final Order resting = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(true).price(100L).initialQuantity(1)
                .build();

        final Order aggressing = Order.builder().clientOrderId(Integer.toString(0))
                .security(fifo).buy(false).price(100L).initialQuantity(1)
                .build();

        // Stop order should be accepted and wait in the stop order queue
        fifoOrderBook.addOrder(stopOrder);

        assertFalse(Optional.ofNullable(fifoOrderBook.getOrderResponses(stopOrder.getId())).orElse(Collections.emptyList()).isEmpty());
        assertSame(OrderType.Stop, fifoOrderBook.getOrderResponses(stopOrder.getId()).get(0).getType());
        assertSame(OrderStatus.New, fifoOrderBook.getOrderResponses(stopOrder.getId()).get(0).getStatus());

        // A limit order at the stop order's trigger price which just rests on the book should not yet trigger the stop order
        fifoOrderBook.addOrder(resting);
        assertFalse(Optional.ofNullable(fifoOrderBook.getOrderResponses(stopOrder.getId())).orElse(Collections.emptyList()).size() > 1);

        // Aggressing limit order should match against the resting limit order, which then should trigger the stop order
        fifoOrderBook.addOrder(aggressing);
        assertSame(OrderType.Limit, fifoOrderBook.getLastOrderResponse(stopOrder.getId()).getType());

        assertFalse(fifoOrderBook.isEmpty());
    }

}
