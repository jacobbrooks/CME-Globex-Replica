import java.math.BigDecimal;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.Arrays;
import java.util.Comparator;

public class PriceLevel {

   private final PriorityBlockingQueue<Order> orders;
   private final BigDecimal price;
   private int totalQuantity;

   public PriceLevel(Order order) {
      this.orders = new PriorityBlockingQueue<>(1, Comparator.comparing(Order::getTimeStamp));
      this.orders.put(order);
      this.price = order.getPrice();
      this.totalQuantity = order.getQuantity();
   }

   public synchronized void matchAgainst(Order order) {
      while(!order.isFilled() && orders.size() > 0) {
         final Order match = orders.peek();
         final int fillQuantity = Math.min(order.getRemainingQuantity(), match.getRemainingQuantity());
         match.fill(fillQuantity);
         order.fill(fillQuantity);
         System.out.println("Match: orderId=" + order.getOrderId() + ", quantity=" + fillQuantity);
         totalQuantity -= fillQuantity;
         if(match.isFilled()) {
            orders.poll();
         }
      }
   }

   public void matchAlong(Order order) {
      orders.add(order);
      totalQuantity += order.getRemainingQuantity();
   }

   public int getTotalQuantity() {
      return totalQuantity;
   }

   public BigDecimal getPrice() {
      return price;
   }

   public String toString() {
      return Arrays.stream(orders.toArray())
         .map(o -> o.toString())
         .reduce("", (concat, ord) -> concat + ord + "\n")
         .trim();
   }

}
