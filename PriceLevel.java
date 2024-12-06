import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.IntStream;

public class PriceLevel {

	private final MatchingAlgorithm matchingAlgorithm;
   private final List<PriorityQueue<OrderContainer>> ordersByMatchStep;
   private final Map<Integer, Order> ordersById;
   private final long price;
   private int totalQuantity;
   
   private final MatchStepComparator matchStepComparator;

   public PriceLevel(long price, MatchingAlgorithm matchingAlgorithm, MatchStepComparator matchStepComparator) {
		this.matchingAlgorithm = matchingAlgorithm;
      this.matchStepComparator = matchStepComparator;
      this.ordersById = new HashMap<Integer, Order>();
      this.price = price;
      this.ordersByMatchStep = new ArrayList<>();
      IntStream.range(0, matchStepComparator.getNumberOfSteps())
         .forEach(i -> ordersByMatchStep.add(new PriorityQueue<OrderContainer>()));
   }

   public List<MatchEvent> match(Order order) {
		final List<MatchEvent> matches = new ArrayList<>();

      int matchStep = 0;
      int ordersMatchedForCurrentStep = 0;
      final int[] initialQueueSizes = ordersByMatchStep.stream().mapToInt(q -> q.size()).toArray();
      
      while(!order.isFilled() && matchStep < ordersByMatchStep.size()) {
         if(ordersByMatchStep.get(matchStep).size() == 0) {
            matchStep++;
            continue;
         }

         final Order match = ordersByMatchStep.get(matchStep).poll().getOrder();

         ordersMatchedForCurrentStep++;

         if(match.getRemainingQuantity() == 0) {
            continue;
         }

         final int minFill = !matchStepComparator.hasStep(MatchStep.ProRata) ? 1 : 0;
         final int aggressingQuantity = Math.max(minFill, getAggressingQuantity(order, match));
         final int fillQuantity = Math.min(aggressingQuantity, match.getRemainingQuantity());

         match.fill(fillQuantity, false);
         order.fill(fillQuantity, match.isTop());
         totalQuantity -= fillQuantity;

         if(!match.isFilled()) {
            ordersByMatchStep.get(matchStep).add(new OrderContainer(match, matchStepComparator, matchStep));
         } else {
            ordersById.remove(match.getId());
         }

         if(fillQuantity > 0) {
			   matches.add(new MatchEvent(order.getId(), match.getId(), price, fillQuantity, order.isBuy(), System.currentTimeMillis()));
         }
         
         // For Allocation: Re-prorate orders WRT postTOPQuantity for the upcoming ProRata pass
         if(match.isTop() && matchingAlgorithm == MatchingAlgorithm.Allocation) {
            ordersByMatchStep.get(matchStep + 1).forEach(cont -> cont.getOrder().updateProration(totalQuantity)); 
         }

         if(ordersMatchedForCurrentStep == initialQueueSizes[matchStep]) {
            ordersMatchedForCurrentStep = 0;
            matchStep++;
         }
      }

      prepareOrdersForNextMatch();

		return matches;
   }

   private int getAggressingQuantity(Order order, Order match) {
      if(matchingAlgorithm == MatchingAlgorithm.FIFO) {
         return order.getRemainingQuantity();
      }
      if(matchStepComparator.hasStep(MatchStep.TOP) && match.isTop()) {
         return order.getInitialQuantity();
      }
      if(matchStepComparator.hasStep(MatchStep.LMM) && match.isLMMAllocatable()) {
			return (int) Math.floor((double) order.getPostTOPQuantity() * match.getLMMAllocationPercentage() / 100);
      }
      if(matchStepComparator.hasStep(MatchStep.ProRata) && match.isProRataAllocatable()) {
         final int lots = (int) Math.floor(order.getPostTOPQuantity() * match.getProration());
         return lots >= 2 ? lots : 0;
      }
      return order.getRemainingQuantity();   
   }

   public void prepareOrdersForNextMatch() {
      if(matchingAlgorithm == MatchingAlgorithm.FIFO) {
         return;
      }
      ordersByMatchStep.forEach(orders -> {
         orders.forEach(o -> {
            o.getOrder().resetMatchingAlgorithmFlags();
         });
      });
      if(matchStepComparator.hasStep(MatchStep.ProRata)) {
         updateProrationsAndResort();
      }
   }

   private void updateProrationsAndResort() {
      final int proRataMatchStep = matchStepComparator.getStepIndex(MatchStep.ProRata);
      final PriorityQueue<OrderContainer> temp = new PriorityQueue<>();
      temp.addAll(ordersByMatchStep.get(proRataMatchStep));
      ordersByMatchStep.get(proRataMatchStep).clear();
      temp.stream().forEach(o -> {
         o.getOrder().updateProration(totalQuantity);
         ordersByMatchStep.get(proRataMatchStep).add(o);
      });
   }

   public void unassignTop() {
      final OrderContainer unTop = ordersByMatchStep.get(0).poll();
      unTop.getOrder().setTop(false);
      ordersByMatchStep.get(0).add(unTop);
   }

	public Order getOrder(int orderId) {
		return ordersById.get(orderId);
	}

	public boolean hasOrder(int orderId) {
		return ordersById.containsKey(orderId);
	}

   public void add(Order order) {
      addOrderToProperQueues(order);
      ordersById.put(order.getId(), order);
      totalQuantity += order.getRemainingQuantity();
      if(matchStepComparator.hasStep(MatchStep.ProRata)) {
         updateProrationsAndResort();
      }
   }

   public int getTotalQuantity() {
      return totalQuantity;
   }

   public long getPrice() {
      return price;
   }

   public boolean isEmpty() {
      return ordersById.isEmpty();
   }

   public String toString() {
      return "$" + price + ": {" + ordersByMatchStep.stream()
         .flatMap(q -> q.stream())
         .distinct()
         .map(o -> "[" + o.toString() + "],")
			.collect(Collectors.joining())
         .trim() + "}";
   }

   private void addOrderToProperQueues(Order order) {
      IntStream.range(0, matchStepComparator.getNumberOfSteps())
         .filter(stepIndex -> matchStepComparator.orderFitsStepCriteria(stepIndex, order))
         .forEach(stepIndex -> ordersByMatchStep.get(stepIndex)
            .add(new OrderContainer(order, matchStepComparator, stepIndex)));
   }

   private static final class OrderContainer implements Comparable<OrderContainer> {

      private final Order order;
      private final MatchStepComparator matchStepComparator;
      private final int matchStep;

      public OrderContainer(Order order, MatchStepComparator matchStepComparator, int matchStep) {
         this.order = order;
         this.matchStepComparator = matchStepComparator;
         this.matchStep = matchStep;
      }

      public Order getOrder() {
         return order;
      }

      public int getMatchStep() {
         return matchStep;
      }

      @Override
      public int compareTo(OrderContainer other) {
         return matchStepComparator.compare(order, other.getOrder(), matchStep); 
      }
 
   }

}
