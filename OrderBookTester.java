import java.util.List;
import java.util.stream.IntStream;

public class OrderBookTester {

	private final OrderBook fifoOrderBook = new OrderBook(new Security(1, MatchingAlgorithm.FIFO));
	private final OrderBook lmmOrderBook = new OrderBook(new Security(1, MatchingAlgorithm.LMM));
   
   public boolean testLMMOrderBookOneLeft() {
		System.out.println("\ntestLMMOrderBookOneLeft()");
		System.out.println("===================================");

		final List<Order> bids = List.of(50, 60).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), 1, true, 100L, 10, p);
         }).toList();

      bids.forEach(o -> {
         lmmOrderBook.addOrder(o, false);
      });

      final Order ask = new Order(Integer.toString(0), 1, false, 100L, 1, 0);
      final OrderResponse response = lmmOrderBook.addOrder(ask, false);

      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getOrderId(), bids.get(0).getOrderId(), 100L, 1, false, 0L)
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

		final List<Order> bids = List.of(0, 20, 80).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), 1, true, 100L, 10, p);
         }).toList();

      bids.forEach(o -> {
         lmmOrderBook.addOrder(o, false);
      });

      final Order ask = new Order(Integer.toString(0), 1, false, 100L, 30, 0);
      final OrderResponse response = lmmOrderBook.addOrder(ask, false);

      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getOrderId(), bids.get(1).getOrderId(), 100L, 6, false, 0L),
         new MatchEvent(ask.getOrderId(), bids.get(2).getOrderId(), 100L, 10, false, 0L),
         new MatchEvent(ask.getOrderId(), bids.get(0).getOrderId(), 100L, 10, false, 0L),
         new MatchEvent(ask.getOrderId(), bids.get(1).getOrderId(), 100L, 4, false, 0L)
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

	public boolean testFIFOOrderBook() {
		System.out.println("\ntestFIFOOrderBook()");
		System.out.println("====================");

		// 5 bid price levels, with the 200 price level having multiple orders (to test time priority)
		final List<Long> bidPrices = List.of(100L, 150L, 200L, 200L, 250L, 300L);
      final List<Long> askPrices = List.of(100L, 150L, 200L, 250L, 300L);

		// Add bids
		bidPrices.forEach(p -> {
			fifoOrderBook.addOrder(new Order(Integer.toString(0), 1, true, p, 10, 0), false);
			hold(10); // So that each order has slightly different timestamps
		});
		
		askPrices.forEach(p -> {
			fifoOrderBook.addOrder(new Order(Integer.toString(0), 1, false, p, 10, 0), false);
			hold(10);
		});

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

	private void hold(long ms) {
		try {Thread.sleep(ms);} catch(InterruptedException e) {e.printStackTrace();}
	}

};
