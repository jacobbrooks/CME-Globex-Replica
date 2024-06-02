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
      this.orders.put(new PriceLevelKey(order.getClientOrderId(), order.getTimestamp()), order);
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
			matches.add(new MatchEvent(order.getClientOrderId(), match.getClientOrderId(), price, fillQuantity, order.isBuy()));
      }
		return matches;
   }

	public Order getOrder(int clientOrderId) {
		return orders.get(new PriceLevelKey(clientOrderId, 0));
	}

	public boolean hasOrder(int clientOrderId) {
		return getOrder(clientOrderId) != null;
	}

   public void add(Order order) {
      orders.put(new PriceLevelKey(order.getClientOrderId(), order.getTimestamp()), order);
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

		private int clientOrderId;
		private long timestamp;

		public PriceLevelKey(int clientOrderId, long timestamp) {
			this.clientOrderId = clientOrderId;
			this.timestamp = timestamp;
		}

		public int getClientOrderId() {
			return clientOrderId;
		}

		public long getTimestamp() {
			return timestamp;
		}

		@Override
		public boolean equals(Object other) {
			return (other instanceof PriceLevelKey) && ((PriceLevelKey) other).getClientOrderId() == clientOrderId;	
		}

		@Override
		public int compareTo(PriceLevelKey other) {
			return equals(other) ? 0 : timestamp > other.getTimestamp() ? 1 : -1;
		}
	}

}
