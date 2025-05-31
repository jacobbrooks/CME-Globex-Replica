package com.cme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

public class OrderTypesTest extends OrderBookTest {

    private final Security fifo = new Security(1, MatchingAlgorithm.FIFO);
    private final OrderBook fifoOrderBook = new OrderBook(fifo);

    @Test
    public void testStopOrder() {
        /*
        final Order stopOrder = new Order(Integer.toString(0), fifo, true, 200L, 10,
                0, OrderType.Stop, OrderDuration.Day, 0, 100L);
        final Order resting = new Order(Integer.toString(0), fifo, true, 100L, 1, 0);
        final Order aggressing = new Order(Integer.toString(0), fifo, false, 100L, 1, 0);*/

        //fifoOrderBook.addOrder(stopOrder);

    }

}
