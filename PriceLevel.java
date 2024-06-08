import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PriceLevel {

	private final MatchingAlgorithm matchingAlgorithm;
   private final TreeMap<PriceLevelKey, Order> orders;
   private final long price;
   private int totalQuantity;

   public PriceLevel(Order order, MatchingAlgorithm matchingAlgorithm) {
		this.matchingAlgorithm = matchingAlgorithm;
      this.orders = new TreeMap<PriceLevelKey, Order>();
      this.orders.put(getKey(order), order);
      this.price = order.getPrice();
      this.totalQuantity = order.getInitialQuantity();
   }

   public List<MatchEvent> match(Order order) {
		final List<MatchEvent> matches = new ArrayList<>();
      while(!order.isFilled() && orders.size() > 0) {
         final Order match = orders.firstEntry().getValue();

         final int fillQuantity = matchingAlgorithm == MatchingAlgorithm.FIFO ? 
				Math.min(order.getRemainingQuantity(), match.getRemainingQuantity()) :
				matchingAlgorithm == MatchingAlgorithm.FIFOWithLMM ?
					(int) Math.floor((double) order.getRemainingQuantity() * match.getAllocationPercentage() / 100) :
					Math.min(order.getRemainingQuantity(), match.getRemainingQuantity());

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
		return orders.get(getKey(orderId));
	}

	public boolean hasOrder(int orderId) {
		return getOrder(orderId) != null;
	}

   public void add(Order order) {
      orders.put(getKey(order), order);
      totalQuantity += order.getRemainingQuantity();
   }

   public int getTotalQuantity() {
      return totalQuantity;
   }

   public long getPrice() {
      return price;
   }

	private PriceLevelKey getKey(Order order) {
		switch(matchingAlgorithm) {
		case FIFO:
			return new FIFOPriceLevelKey(order.getOrderId(), order.getTimestamp());
		case FIFOWithLMM:
			return new FIFOWithLMMPriceLevelKey(order.getOrderId(), order.getTimestamp(), order.getAllocationPercentage());
		default:
			return null;
		}
	}

	private PriceLevelKey getKey(int orderId) {
		switch(matchingAlgorithm) {
		case FIFO:
			return new FIFOPriceLevelKey(orderId);
		case FIFOWithLMM:
			return new FIFOWithLMMPriceLevelKey(orderId);
		default:
			return null;
		}
	}

   public String toString() {
      return "$" + price + ": {" + orders.entrySet().stream()
         .map(o -> "[" + o.getValue().toString() + "],")
			.collect(Collectors.joining())
         .trim() + "}";
   }

}
