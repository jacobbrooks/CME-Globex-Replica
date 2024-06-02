import java.util.concurrent.atomic.AtomicInteger;

public class Order {

   private static final AtomicInteger NEXT_ORDER_ID = new AtomicInteger(0);
  
	private final int clientOrderId;
	private final int securityId; 
   private final long timestamp;
   private final int orderId;

   private final boolean buy;
   private long price;
   private int initialQuantity;
   
   private int filledQuantity;

   public Order(int clientOrderId, int securityId, boolean buy, long price, int initialQuantity) {
		this.clientOrderId = clientOrderId;
      this.timestamp = System.currentTimeMillis();
		this.securityId = securityId;
      this.buy = buy;
      this.price = price;
      this.initialQuantity = initialQuantity;
      this.orderId = NEXT_ORDER_ID.incrementAndGet();
   }

	public int getClientOrderId() {
		return clientOrderId;
	}

   public long getTimestamp() {
      return timestamp;
   }

   public int getOrderId() {
      return orderId;
   }
	
	public int getSecurityId() {
		return securityId;
	}

   public long getPrice() {
      return price;
   }

   public int getInitialQuantity() {
      return initialQuantity;
   }

   public boolean isBuy() {
      return buy;
   }

   public int getFilledQuantity() {
      return filledQuantity;
   }

   public int getRemainingQuantity() {
      return initialQuantity - filledQuantity;
   }

   public void fill(int initialQuantity) {
      filledQuantity += initialQuantity;
   }

   public boolean isFilled() {
      return getRemainingQuantity() == 0;
   }

   public String toString() {
      return "$" + price + " - " + getRemainingQuantity() + " @" + timestamp + "ms"; 
   }

}
