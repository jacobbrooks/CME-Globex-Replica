import java.util.concurrent.PriorityBlockingQueue;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;

public class OrderBook {

   private final Security security;
   private final PriorityBlockingQueue<PriceLevel> bids;
   private final PriorityBlockingQueue<PriceLevel> asks;
   private final Map<String, PriceLevel> priceLevelMap;
   
   public OrderBook(Security security) {
      this.security = security;
      final Comparator<PriceLevel> askComparator = Comparator.comparing(PriceLevel::getPrice);
      bids = new PriorityBlockingQueue<>(1, askComparator.reversed());
      asks = new PriorityBlockingQueue<>(1, askComparator);
      priceLevelMap = new HashMap<>();
   }

   public synchronized OrderResponse request(Order order, boolean print) throws InterruptedException {
      final PriorityBlockingQueue<PriceLevel> matchAgainst = order.isBuy() ? asks : bids;
      final int matchFlag = order.isBuy() ? 1 : -1;

      while(!order.isFilled()) {
         final PriceLevel best = matchAgainst.peek();
         if(best == null) {
            break;
         }
         final int comparison = order.getPrice().compareTo(best.getPrice());
         final boolean isMatch = comparison == 0 || comparison == matchFlag;
         if(!isMatch) {
            break;
         } 
         best.matchAgainst(order);
         if(best.getTotalQuantity() == 0) {
            matchAgainst.poll();
         } 
      }
      
      if(order.isFilled()) {
         return new OrderResponse();
      }
     
      final PriorityBlockingQueue<PriceLevel> resting = order.isBuy() ? bids : asks;
      if(priceLevelMap.containsKey(order.isBuy() + order.getPrice().toString())) {
         PriceLevel existing = priceLevelMap.get(order.getPrice().toString());
         existing.matchAlong(order);
      } else {
         final PriceLevel newPriceLevel = new PriceLevel(order);
         resting.put(newPriceLevel);
         priceLevelMap.put(order.isBuy() + order.getPrice().toString(), newPriceLevel);
      }
      return new OrderResponse();
   }

   public void printBook() {
      System.out.println("============ Bids " + bids.size() + " ==============");
      Arrays.stream(this.bids.toArray()).map(b -> b.toString()).forEach(System.out::println);
      System.out.println("============ Asks " + asks.size() + " ==============");
      Arrays.stream(this.asks.toArray()).map(a -> a.toString()).forEach(System.out::println);
   }
   
}
