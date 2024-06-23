import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class OrderBookTester {

   private final Security fifo = new Security(1, MatchingAlgorithm.FIFO);
   private final Security lmm = new Security(1, MatchingAlgorithm.LMM);
   private final Security lmmTop = new Security(1, MatchingAlgorithm.LMMWithTOP);

	private final OrderBook fifoOrderBook = new OrderBook(fifo);
	private final OrderBook lmmOrderBook = new OrderBook(lmm);
   private final OrderBook lmmTopOrderBook = new OrderBook(lmmTop);

   public boolean testLMMWithTopOrderBook() {
		System.out.println("\ntestLMMWithTopOrderBook()");
		System.out.println("===================================");
   
      // Top order should be the first to market	
      final List<Order> bids = List.of(0, 10).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmmTop, true, 100L, 10, p);
         }).toList();

      bids.forEach(o -> {
         lmmTopOrderBook.addOrder(o, false);
      });

      List<Order> top = bids.stream().filter(Order::isTop).toList();
      final String topOrderId = "Order id: " + top.stream()
         .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

      if(top.size() != 1) {
         printTestFail("Not exactly 1 top order");
         return false;
      }

      if(!bids.get(0).isTop()) {
         printTestFail("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }

      /*
       * First to best the market should be the new top, so the first order at this 200 
       * price level should dethrone the original top order
       */
      final List<Order> higherBids = List.of(0, 10).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmmTop, true, 200L, 10, p);
         }).toList();
      
      higherBids.forEach(o -> {
         lmmTopOrderBook.addOrder(o, false);
      });
      
      top = Stream.concat(bids.stream().filter(Order::isTop), higherBids.stream().filter(Order::isTop)).toList();
      if(top.size() != 1) {
         printTestFail("Not exactly 1 top order");
         return false;
      }

      if(!higherBids.get(0).isTop() || bids.get(0).isTop()) {
         printTestFail("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }
     
      final Order ask = new Order(Integer.toString(0), lmmTop, false, 100L, 20, 0);
     
      /* 
       * We expect the 200 ask to first match against the top order for 10 lots, 
       * then the LMM order for its LMM allocation percentage (10% * 20 lots = 2 lots), then 
       * one more match against the LMM order for the rest of the qty on the price level (8 lots)
       */
      final OrderResponse response = lmmTopOrderBook.addOrder(ask, false);
      List<MatchEvent> matches = response.getMatchesByPrice().get(200L);
      List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), higherBids.get(0).getId(), 200L, 10, false, 0L),
         new MatchEvent(ask.getId(), higherBids.get(1).getId(), 200L, 2, false, 0L),
         new MatchEvent(ask.getId(), higherBids.get(1).getId(), 200L, 8, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }
        
		System.out.println("TEST SUCCESS");
      return true;
   }
   
   public boolean testLMMOrderBookOneLeft() {
		System.out.println("\ntestLMMOrderBookOneLeft()");
		System.out.println("===================================");
      
      lmmOrderBook.clear();

		final List<Order> bids = List.of(50, 60).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmm, true, 100L, 10, p);
         }).toList();

      bids.forEach(o -> {
         lmmOrderBook.addOrder(o, false);
      });

      final Order ask = new Order(Integer.toString(0), lmm, false, 100L, 1, 0);
      final OrderResponse response = lmmOrderBook.addOrder(ask, false);

      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L)
      );

      final List<MatchEvent> matches = response.getMatchesByPrice().get(100L);
      final boolean success = matches.size() == expectedMatches.size() 
         && !IntStream.range(0, matches.size())
               .anyMatch(i -> matches.get(i).getRestingOrderId() != expectedMatches.get(i).getRestingOrderId() 
                  || matches.get(i).getMatchQuantity() != expectedMatches.get(i).getMatchQuantity());

      if(!success) {
         printTestFail("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }

		System.out.println("TEST SUCCESS");
      return true;
   }   

   public boolean testLMMOrderBook() {
		System.out.println("\ntestLMMOrderBook()");
		System.out.println("==========================");
      
      lmmOrderBook.clear();

		final List<Order> bids = List.of(0, 20, 80).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmm, true, 100L, 10, p);
         }).toList();

      bids.forEach(o -> {
         lmmOrderBook.addOrder(o, false);
      });

      final Order ask = new Order(Integer.toString(0), lmm, false, 100L, 30, 0);
      final OrderResponse response = lmmOrderBook.addOrder(ask, false);

      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 6, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 10, false, 0L),
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 10, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 4, false, 0L)
      );
      final List<MatchEvent> matches = response.getMatchesByPrice().get(100L);

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }

		System.out.println("TEST SUCCESS");
      return true;
   }

	public boolean testFIFOOrderBook() {
		System.out.println("\ntestFIFOOrderBook()");
		System.out.println("====================");

		// 5 bid price levels, with the 200 price level having multiple orders (to test time priority)
		final List<Long> bidPrices = List.of(100L, 150L, 200L, 200L, 250L, 300L);
      final List<Long> askPrices = List.of(100L, 150L, 200L, 250L, 300L);

      final List<Order> bids = bidPrices.stream()
         .map(p -> {
			   hold(10); // So that each order has slightly different timestamps
            return new Order(Integer.toString(0), fifo, true, p, 10, 0);
         }).toList();

      final List<Order> asks = askPrices.stream()
         .map(p -> {
			   hold(10); // So that each order has slightly different timestamps
            return new Order(Integer.toString(0), fifo, false, p, 10, 0);
         }).toList();

		// Add bids
		bids.forEach(b -> {
			fifoOrderBook.addOrder(b, false);
		});
		
		final List<OrderResponse> responses = asks.stream().map(a -> {
			return fifoOrderBook.addOrder(a, false);
		}).toList();

      final List<MatchEvent> matches = responses.stream()
         .map(r -> r.getMatchesByPrice().entrySet().stream().map(e -> e.getValue()).findFirst())
         .filter(opt -> opt.isPresent())
         .map(opt -> opt.get().get(0))
         .toList();
      
      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(asks.get(0).getId(), bids.get(5).getId(), 300L, 10, false, 0L),
         new MatchEvent(asks.get(1).getId(), bids.get(4).getId(), 250L, 10, false, 0L),
         new MatchEvent(asks.get(2).getId(), bids.get(2).getId(), 200L, 10, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
      }
      
		/*
		 * Resulting order book state should be bids: {200, 150, 100}, asks: {250, 300}
		 */
		final List<Long> expectedBids = List.of(200L, 150L, 100L);
		final List<Long> expectedAsks = List.of(250L, 300L);
		final List<Long> actualBids = fifoOrderBook.getBidPrices();
		final List<Long> actualAsks = fifoOrderBook.getAskPrices();

		if(!(actualBids.size() == expectedBids.size() && actualAsks.size() == expectedAsks.size())) {
			printTestFail("bids", expectedBids.stream().map(l -> l.toString()).toList(), actualBids.stream().map(l -> l.toString()).toList());
			printTestFail("asks", expectedAsks.stream().map(l -> l.toString()).toList(), actualAsks.stream().map(l -> l.toString()).toList());
			return false;
		}

		if(IntStream.range(0, expectedBids.size()).anyMatch(i -> expectedBids.get(i) != actualBids.get(i).longValue())) {
			printTestFail("bids", expectedBids.stream().map(l -> l.toString()).toList(), actualBids.stream().map(l -> l.toString()).toList());
			printTestFail("asks", expectedAsks.stream().map(l -> l.toString()).toList(), actualAsks.stream().map(l -> l.toString()).toList());
			return false;
		}
	
		if(IntStream.range(0, expectedAsks.size()).anyMatch(i -> expectedAsks.get(i) != actualAsks.get(i).longValue())) {
			printTestFail("bids", expectedBids.stream().map(l -> l.toString()).toList(), actualBids.stream().map(l -> l.toString()).toList());
			printTestFail("asks", expectedAsks.stream().map(l -> l.toString()).toList(), actualAsks.stream().map(l -> l.toString()).toList());
			return false;
		}

		System.out.println("TEST SUCCESS");
		return true;
	}

   private void printTestFail(String message) {
      System.out.println("TEST FAIL");
      System.out.println("------------------------------------");
      System.out.println(message);
   }

	private void printTestFail(String criteria, List<String> expected, List<String> actual) {
      System.out.println("TEST FAIL");
      System.out.println("------------------------------------");
		System.out.println("Expected " + criteria + ": [");
		expected.forEach(b -> System.out.println("  " + b + ","));
		System.out.println("], \nActual " + criteria + ": [");
		actual.forEach(b -> System.out.println("  " + b + ", "));
		System.out.print("]\n");
		System.out.println();
	} 
   
   private boolean equalMatches(List<MatchEvent> expected, List<MatchEvent> actual) {
      return actual.size() == expected.size() 
         && !IntStream.range(0, actual.size())
               .anyMatch(i -> actual.get(i).getRestingOrderId() != expected.get(i).getRestingOrderId() 
                  || actual.get(i).getMatchQuantity() != expected.get(i).getMatchQuantity());
   }

	private void hold(long ms) {
		try {Thread.sleep(ms);} catch(InterruptedException e) {e.printStackTrace();}
	}

};
