import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
   public static void main(String[] args) {
		OrderBookTester orderBookTester = new OrderBookTester();
		boolean success = orderBookTester.testFIFOOrderBook();
      boolean success2 = orderBookTester.testLMMOrderBook();
      boolean success3 = orderBookTester.testLMMOrderBookOneLeft();
      boolean success4 = orderBookTester.testLMMWithTopOrderBook();
   }

	private static void testOrderBookViaGateway() throws InterruptedException {
		final OrderGateway gateway = new OrderGateway();
		gateway.start();

      final List<Long> bidPrices = List.of(100L, 150L, 200L, 250L, 300L);
      final List<Long> askPrices = List.of(100L, 150L, 200L, 250L, 300L);
		final List<Integer> bidAllocationPercentages = List.of(0, 0, 0, 50, 0, 0);
      final Security security = new Security(1, MatchingAlgorithm.FIFO);

		final AtomicInteger idGenerator = new AtomicInteger(-1);
      
      Runnable bidTask = () -> {
			final AtomicInteger idx = new AtomicInteger(0);
      	bidPrices.forEach(p -> {
				final int id = idGenerator.incrementAndGet();
				gateway.submit(new Order(Integer.toString(id), security, true, p, 10, bidAllocationPercentages.get(idx.getAndIncrement())));
			});
      };

      Runnable askTask = () -> {
      	askPrices.forEach(p -> gateway.submit(new Order(Integer.toString(idGenerator.incrementAndGet()), security, false, p, 10, 0)));
      }; 

      Thread bidThread = new Thread(bidTask);
      Thread askThread = new Thread(askTask);
      
      bidThread.start();
		try {Thread.sleep(2000);} catch(InterruptedException e) {}
      askThread.start();

      bidThread.join();
      askThread.join();
    
		try {Thread.sleep(1000);} catch(InterruptedException e) {}
 
      gateway.printBook(1);
	}
}
