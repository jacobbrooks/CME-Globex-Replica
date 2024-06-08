import java.util.List;
import java.util.stream.IntStream;

public class OrderBookTester {

	private final OrderBook fifoOrderBook = new OrderBook(new Security(1, MatchingAlgorithm.FIFO));
	private final OrderBook fifoWithLMMOrderbook = new OrderBook(new Security(1, MatchingAlgorithm.FIFOWithLMM));

	public boolean testFIFOOrderBook() {
		System.out.println("testFIFOOrderBook()");
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
			System.out.println("TEST FAIL");
			System.out.println("---------");
			printTestFail("bids", expectedBids, actualBids);
			printTestFail("asks", expectedAsks, actualAsks);
			return false;
		}

		if(IntStream.range(0, expectedBids.size()).anyMatch(i -> expectedBids.get(i) != actualBids.get(i).longValue())) {
			System.out.println("TEST FAIL");
			System.out.println("---------");
			printTestFail("bids", expectedBids, actualBids);
			printTestFail("asks", expectedAsks, actualAsks);
			return false;
		}
	
		if(IntStream.range(0, expectedAsks.size()).anyMatch(i -> expectedAsks.get(i) != actualAsks.get(i).longValue())) {
			System.out.println("TEST FAIL");
			System.out.println("---------");
			printTestFail("bids", expectedBids, actualBids);
			printTestFail("asks", expectedAsks, actualAsks);
			return false;
		}

		System.out.println("TEST SUCCESS");
		return true;
	}

	private void printTestFail(String criteria, List<Long> expected, List<Long> actual) {
		System.out.print("Expected " + criteria + ": [");
		expected.forEach(b -> System.out.print(b + ","));
		System.out.print("], Actual " + criteria + ": [");
		actual.forEach(b -> System.out.print(b + ", "));
		System.out.print("]\n");
		System.out.println();
	} 

	private void hold(long ms) {
		try {Thread.sleep(ms);} catch(InterruptedException e) {e.printStackTrace();}
	}

};
