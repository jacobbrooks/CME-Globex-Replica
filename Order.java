import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

public class Order {

   private static final AtomicInteger NEXT_ORDER_ID = new AtomicInteger(0);
   
   private final long timeStamp;
   private final int orderId;

   private final String clientId; 
   private final boolean buy;
   private BigDecimal price;
   private int quantity;
   
   private int filledQuantity;

   public Order(String clientId, boolean buy, BigDecimal price, int quantity) {
      this.timeStamp = System.currentTimeMillis();
      this.buy = buy;
      this.clientId = clientId;
      this.price = price;
      this.quantity = quantity;
      this.orderId = NEXT_ORDER_ID.incrementAndGet();
   }

   public long getTimeStamp() {
      return timeStamp;
   }

   public int getOrderId() {
      return orderId;
   }

   public String getClientId() {
      return clientId;
   }

   public BigDecimal getPrice() {
      return price;
   }

   public int getQuantity() {
      return quantity;
   }

   public boolean isBuy() {
      return buy;
   }

   public int getFilledQuantity() {
      return filledQuantity;
   }

   public int getRemainingQuantity() {
      return quantity - filledQuantity;
   }

   public void fill(int quantity) {
      filledQuantity += quantity;
   }

   public boolean isFilled() {
      return getRemainingQuantity() == 0;
   }

   public String toString() {
      return "$" + price.toString() + " - " + getRemainingQuantity() + " @" + timeStamp + "ms"; 
   }

}
