import java.util.concurrent.atomic.AtomicInteger;

public class Order {

   private static final AtomicInteger NEXT_ORDER_ID = new AtomicInteger(0);
  
	private final String clientOrderId;
	private final int securityId; 
   private final long timestamp;
   private final int orderId;
   private final long price;
   private final int initialQuantity;
   private final boolean buy;

   private boolean top;
	private int allocationPercentage;
   private int filledQuantity;

   public Order(String clientOrderId, int securityId, boolean buy, long price, int initialQuantity, int allocationPercentage) {
		this.clientOrderId = clientOrderId;
      this.timestamp = System.currentTimeMillis();
		this.securityId = securityId;
      this.buy = buy;
      this.price = price;
      this.initialQuantity = initialQuantity;
		this.allocationPercentage = allocationPercentage;
      this.orderId = NEXT_ORDER_ID.incrementAndGet();
   }

   public void setTop(boolean top) {
      this.top = top;
   }

	public String getClientOrderId() {
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

	public int getAllocationPercentage() {
		return allocationPercentage;
	}

   public int getFilledQuantity() {
      return filledQuantity;
   }

   public int getRemainingQuantity() {
      return initialQuantity - filledQuantity;
   }

   public void fill(int quantity) {
      filledQuantity += quantity;
      allocationPercentage = 0;
   }

   public boolean isFilled() {
      return getRemainingQuantity() == 0;
   }

   public boolean isTop() {
      return top;
   }

   public String toString() {
      return "#" + orderId + " - " + getRemainingQuantity() + " @" + timestamp + "ms, " + allocationPercentage + "%"; 
   }

}
