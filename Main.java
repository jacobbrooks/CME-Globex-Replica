import java.math.BigDecimal;

public class Main {
   public static void main(String[] args) throws InterruptedException {
      OrderBook book = new OrderBook(new Security("Apple $180 call"));
      String[] prices = {"1", "1.50", "2", "2.50", "3"};
      
      Runnable bidTask = () -> {
         for(String s : prices) {
            try {
               OrderResponse response = book.request(new Order("myHedgeFund123", true, new BigDecimal(s), 10), false);
            } catch(InterruptedException e) {
               e.printStackTrace();
            }
         }
      };

      Runnable askTask = () -> {
         for(String s : prices) {
            try {
               OrderResponse response = book.request(new Order("myHedgeFund123", false, new BigDecimal(s), 5), true);
            } catch(InterruptedException e) {
               e.printStackTrace();
            }
         }
      };

      Thread bidThread = new Thread(bidTask);
      Thread askThread = new Thread(askTask);
      
      bidThread.start();
      askThread.start();

      bidThread.join();
      askThread.join();
      
      book.printBook();
   }
}
