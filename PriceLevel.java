import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class PriceLevel {

	private final MatchingAlgorithm matchingAlgorithm;
   private final PriorityQueue<Order> orders;
   private final Map<Integer, Order> ordersById;
   private final long price;
   private int totalQuantity;

   public PriceLevel(Order order, MatchingAlgorithm matchingAlgorithm) {
		this.matchingAlgorithm = matchingAlgorithm;
      this.orders = new PriorityQueue<Order>();
      this.ordersById = new HashMap<Integer, Order>();
      this.orders.add(order);
      this.ordersById.put(order.getId(), order);
      this.price = order.getPrice();
      this.totalQuantity = order.getInitialQuantity();
   }

   public List<MatchEvent> match(Order order) {
		final List<MatchEvent> matches = new ArrayList<>();

      while(!order.isFilled() && orders.size() > 0) {
         final Order match = orders.poll();

         final int aggressingQuantity = Math.max(1, 
            matchingAlgorithm == MatchingAlgorithm.FIFO ? 
               order.getRemainingQuantity() :
               List.of(MatchingAlgorithm.LMM, MatchingAlgorithm.LMMWithTOP).contains(matchingAlgorithm) 
                  && match.getAllocationPercentage() > 0 ?
					      (int) Math.floor((double) order.getInitialQuantity() * match.getAllocationPercentage() / 100) :
                     order.getRemainingQuantity()
         );
      
         final int fillQuantity = Math.min(aggressingQuantity, match.getRemainingQuantity());
         match.fill(fillQuantity);
         order.fill(fillQuantity);
         totalQuantity -= fillQuantity;

         if(!match.isFilled()) {
            orders.add(match);
         } else {
            ordersById.remove(match.getId());
         }

			matches.add(new MatchEvent(order.getId(), match.getId(), price, fillQuantity, order.isBuy(), System.currentTimeMillis()));
      }

		return matches;
   }

   public void unassignTop() {
      final Order unTop = orders.poll();
      unTop.setTop(false);
      orders.add(unTop);
   }

	public Order getOrder(int orderId) {
		return ordersById.get(orderId);
	}

	public boolean hasOrder(int orderId) {
		return ordersById.containsKey(orderId);
	}

   public void add(Order order) {
      orders.add(order);
      ordersById.put(order.getId(), order);
      totalQuantity += order.getRemainingQuantity();
   }

   public int getTotalQuantity() {
      return totalQuantity;
   }

   public long getPrice() {
      return price;
   }

   public String toString() {
      return "$" + price + ": {" + orders.stream()
         .map(o -> "[" + o.toString() + "],")
			.collect(Collectors.joining())
         .trim() + "}";
   }

}
