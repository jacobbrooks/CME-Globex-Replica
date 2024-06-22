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
         final PriceLevelKey matchKey = orders.firstEntry().getKey();

         PriceLevelKey k = getKey(match.getOrderId());
         PriceLevelKey k1 = getKey(match);
         
         if(orders.get(k1) == null) {
            System.out.println("DEFECTIVE KEY");
            System.out.println("MATCH HASH: " + matchKey.hashCode());
            System.out.println("K HASH: " + k.hashCode());
            System.out.println("K1 HASH: " + k1.hashCode());
         }

         final int aggressingQuantity = Math.max(1, 
            matchingAlgorithm == MatchingAlgorithm.FIFO ? 
               order.getRemainingQuantity() :
               matchingAlgorithm == MatchingAlgorithm.LMM && match.getAllocationPercentage() > 0 ?
					   (int) Math.floor((double) order.getInitialQuantity() * match.getAllocationPercentage() / 100) :
                  order.getRemainingQuantity()
         );
      
         final int fillQuantity = Math.min(aggressingQuantity, match.getRemainingQuantity());
         match.fill(fillQuantity);
         order.fill(fillQuantity);
         totalQuantity -= fillQuantity;

         if(match.isFilled()) {
            orders.pollFirstEntry();
         } else if(matchingAlgorithm != MatchingAlgorithm.FIFO) {
            orders.remove(matchKey);
            orders.put(getKey(match), match);
            //sift(match);
         }

			matches.add(new MatchEvent(order.getOrderId(), match.getOrderId(), price, fillQuantity, order.isBuy(), System.currentTimeMillis()));
      }

		return matches;
   }

   public void assignTop(Order order, boolean top) {
      order.setTop(top);
      sift(order);
   }

   private void sift(Order order) {
      orders.remove(getKey(order));
      orders.put(getKey(order), order);
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
		case LMM:
			return new LMMPriceLevelKey(order.getOrderId(), order.getTimestamp(), order.getAllocationPercentage());
      case LMMWithTOP:
         return new LMMWithTOPPriceLevelKey(order.getOrderId(), order.getTimestamp(), order.getAllocationPercentage(), order.isTop());
		default:
			return null;
		}
	}

	private PriceLevelKey getKey(int orderId) {
		switch(matchingAlgorithm) {
		case FIFO:
			return new FIFOPriceLevelKey(orderId);
		case LMM:
			return new LMMPriceLevelKey(orderId);
      case LMMWithTOP:
         return new LMMWithTOPPriceLevelKey(orderId);
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
