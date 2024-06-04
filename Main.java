import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
   public static void main(String[] args) throws InterruptedException {

		final OrderGateway gateway = new OrderGateway();
		gateway.start();

      final List<Long> bidPrices = List.of(100L, 150L, 200L, 200L, 250L, 300L);
      final List<Long> askPrices = List.of(100L, 150L, 175L, 200L, 250L, 300L);
		final AtomicInteger idGenerator = new AtomicInteger(-1);
      
      Runnable bidTask = () -> {
      	bidPrices.forEach(p -> gateway.submit(new Order(Integer.toString(idGenerator.incrementAndGet()), 1, true, p, 10)));
      };


      Runnable askTask = () -> {
      	askPrices.forEach(p -> gateway.submit(new Order(Integer.toString(idGenerator.incrementAndGet()), 1, false, p, 10)));
      }; 

      Thread bidThread = new Thread(bidTask);
      Thread askThread = new Thread(askTask);
      
      bidThread.start();
      askThread.start();

      bidThread.join();
      askThread.join();
    
		try {Thread.sleep(1000);} catch(InterruptedException e) {}
 
      gateway.printBook(1);
   }
}
