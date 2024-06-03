import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PriceLevel {

   private final TreeMap<PriceLevelKey, Order> orders;
   private final long price;
   private int totalQuantity;

   public PriceLevel(Order order) {
      this.orders = new TreeMap<PriceLevelKey, Order>();
      this.orders.put(new PriceLevelKey(order.getOrderId(), order.getTimestamp()), order);
      this.price = order.getPrice();
      this.totalQuantity = order.getInitialQuantity();
   }

   public List<MatchEvent> match(Order order) {
		final List<MatchEvent> matches = new ArrayList<>();
      while(!order.isFilled() && orders.size() > 0) {
         final Order match = orders.firstEntry().getValue();
         final int fillQuantity = Math.min(order.getRemainingQuantity(), match.getRemainingQuantity());
         match.fill(fillQuantity);
         order.fill(fillQuantity);
         totalQuantity -= fillQuantity;
         if(match.isFilled()) {
            orders.pollFirstEntry();
         }
			matches.add(new MatchEvent(order.getOrderId(), match.getOrderId(), price, fillQuantity, order.isBuy()));
      }
		return matches;
   }

	public Order getOrder(int orderId) {
		return orders.get(new PriceLevelKey(orderId));
	}

	public boolean hasOrder(int orderId) {
		return getOrder(orderId) != null;
	}

   public void add(Order order) {
      orders.put(new PriceLevelKey(order.getOrderId(), order.getTimestamp()), order);
      totalQuantity += order.getRemainingQuantity();
   }

   public int getTotalQuantity() {
      return totalQuantity;
   }

   public long getPrice() {
      return price;
   }

   public String toString() {
      return orders.entrySet().stream()
         .map(o -> o.getValue().toString())
         .reduce("", (concat, ord) -> concat + ord + "\n")
         .trim();
   }

	private static class PriceLevelKey implements Comparable<PriceLevelKey> {

		private int orderId;
		private long timestamp;

		public PriceLevelKey(int orderId, long timestamp) {
			this.orderId = orderId;
			this.timestamp = timestamp;
		}
	
		public PriceLevelKey(int orderId) {
			this.orderId = orderId;
		}

		public int getOrderId() {
			return orderId;
		}

		public long getTimestamp() {
			return timestamp;
		}

		@Override
		public boolean equals(Object other) {
			return other != null && (other instanceof PriceLevelKey) && ((PriceLevelKey) other).getOrderId() == orderId;
		}

		@Override
		public int hashCode() {
			return orderId;
		}

		@Override
		public int compareTo(PriceLevelKey other) {
			return equals(other) ? 0 : timestamp > other.getTimestamp() ? 1 : -1;
		}
	}

}
