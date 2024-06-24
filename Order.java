import java.util.concurrent.atomic.AtomicInteger;
import java.util.Comparator;

public class Order implements Comparable<Order> {

   private static final AtomicInteger NEXT_ID = new AtomicInteger(0);
  
   private final int id;
	private final String clientOrderId;
	private final Security security; 
   private final long timestamp;
   private final long price;
   private final int initialQuantity;
   private final boolean buy;
   private final OrderComparator comparator;

   private boolean top;
	private int allocationPercentage;
   private int filledQuantity;
   private int filledByTopOrderQuantity;
   private double proration;

   public Order(String clientOrderId, Security security, boolean buy, long price, int initialQuantity, int allocationPercentage) {
      this.id = NEXT_ID.incrementAndGet();
		this.clientOrderId = clientOrderId;
      this.security = security;
      this.timestamp = System.currentTimeMillis();
      this.buy = buy;
      this.price = price;
      this.initialQuantity = initialQuantity;
		this.allocationPercentage = allocationPercentage;
      this.comparator = getComparator(security.getMatchingAlgorithm());
   }

   public void updateProration(int totalPriceLevelQuantity) {
      this.proration = (double) getRemainingQuantity() / totalPriceLevelQuantity;
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

   public int getId() {
      return id;
   }
	
	public Security getSecurity() {
		return security;
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

   public int getRemainingQuantityAfterTopOrderMatch() {
      return initialQuantity - filledByTopOrderQuantity;
   }
      
   public void fill(int quantity, boolean topOrderMatch) {
      filledQuantity += quantity;
      allocationPercentage = 0;
      if(topOrderMatch) {
        filledByTopOrderQuantity = quantity; 
      }
   }

   public boolean isFilled() {
      return getRemainingQuantity() == 0;
   }

   public boolean isTop() {
      return top;
   }

   public double getProration() {
      return proration;
   }

   @Override
   public int compareTo(Order other) {
      return comparator.compare(this, other); 
   }

   public String toString() {
      return "#" + id + " - " + getRemainingQuantity() + " @" + timestamp + "ms, " + allocationPercentage + "%"; 
   }

   private OrderComparator getComparator(MatchingAlgorithm matchingAlgorithm) {
      switch(matchingAlgorithm) {
      case FIFO:
         return new FIFOComparator();
      case LMM: 
         return new LMMComparator();
      case LMMWithTOP:
         return new LMMWithTOPComparator();
      case ProRata:
         return new ProRataComparator();
      default:
         return null;
      }
   }
}
