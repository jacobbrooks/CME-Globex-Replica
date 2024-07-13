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

         final int minFill = !List.of(MatchingAlgorithm.ProRata, MatchingAlgorithm.Allocation)
            .contains(matchingAlgorithm) ? 1 : 0;
         final int aggressingQuantity = Math.max(minFill, getAggressingQuantity(order, match));
         final int fillQuantity = Math.min(aggressingQuantity, match.getRemainingQuantity());

         match.fill(fillQuantity, false);
         order.fill(fillQuantity, match.isTop());
         totalQuantity -= fillQuantity;

         if(!match.isFilled()) {
            orders.add(match);
         } else {
            ordersById.remove(match.getId());
         }

         if(fillQuantity > 0) {
			   matches.add(new MatchEvent(order.getId(), match.getId(), price, fillQuantity, order.isBuy(), System.currentTimeMillis()));
         }

         if(match.isTop() && matchingAlgorithm == MatchingAlgorithm.Allocation) {
            updateProrations();
         }
      }

      prepareOrdersForNextMatch();

		return matches;
   }

   private int getAggressingQuantity(Order order, Order match) {
      if(matchingAlgorithm == MatchingAlgorithm.FIFO) {
         return order.getRemainingQuantity();
      }
      if(List.of(MatchingAlgorithm.LMMWithTOP, MatchingAlgorithm.Allocation).contains(matchingAlgorithm)
            && match.isTop()) {
         return order.getRemainingQuantity();
      }
      if(List.of(MatchingAlgorithm.LMM, MatchingAlgorithm.LMMWithTOP).contains(matchingAlgorithm)
            && match.isLMMAllocatable()) {
			return (int) Math.floor((double) order.getInitialQuantity() * match.getLMMAllocationPercentage() / 100);
      }
      if(List.of(MatchingAlgorithm.ProRata, MatchingAlgorithm.Allocation).contains(matchingAlgorithm) 
            && match.isProRataAllocatable()) {
         final int lots = (int) Math.floor(order.getRemainingQuantityAfterTopOrderMatch() * match.getProration());
         return lots >= 2 ? lots : 0;
      }
      return order.getRemainingQuantity();   
   }

   public void updateProrations() {
      final Order[] ordersSnapshot = new Order[orders.size()];
      orders.toArray(ordersSnapshot);
      orders.clear();
      Arrays.stream(ordersSnapshot).forEach(o -> {
         o.updateProration(totalQuantity);
         orders.add(o);
      });
   }

   public void prepareOrdersForNextMatch() {
      if(matchingAlgorithm == MatchingAlgorithm.FIFO) {
         return;
      }
      // Order here is important, flags need to be reset before re-insertion during updateProrations()
      orders.forEach(o -> {
         o.resetMatchingAlgorithmFlags();
      });
      updateProrations();
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
