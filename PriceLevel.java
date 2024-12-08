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

      int matchStepIndex = 0;
      int ordersMatchedForCurrentStep = 0;
      final int[] initialQueueSizes = ordersByMatchStep.stream().mapToInt(q -> q.size()).toArray();

      while(!order.isFilled() && matchStepIndex < ordersByMatchStep.size()) {
         if(ordersByMatchStep.get(matchStepIndex).size() == 0) {
            prepareForNextMatchStep(order, matchStepIndex + 1, initialQueueSizes);
            matchStepIndex++;
            continue;
         }

         final Order match = ordersByMatchStep.get(matchStepIndex).poll().getOrder();
         ordersMatchedForCurrentStep++;

         final int minFill = !matchStepComparator.hasStep(MatchStep.ProRata) ? 1 : 0;
         final int aggressingQuantity = Math.max(minFill, getAggressingQuantity(order, match, matchStepIndex));
         final int fillQuantity = Math.min(aggressingQuantity, match.getRemainingQuantity());

         match.fill(fillQuantity, matchStepComparator.getMatchStep(matchStepIndex));
         order.fill(fillQuantity, matchStepComparator.getMatchStep(matchStepIndex));
         totalQuantity -= fillQuantity;

         if(!match.isFilled()) {
            ordersByMatchStep.get(matchStepIndex).add(new OrderContainer(match, matchStepComparator, matchStepIndex));
         } else {
            ordersById.remove(match.getId());
         }

         if(fillQuantity > 0) {
			   matches.add(new MatchEvent(order.getId(), match.getId(), price, fillQuantity, order.isBuy(), System.currentTimeMillis()));
         } else if(matchStepComparator.getStepIndex(MatchStep.ProRata) == matchStepIndex) {
            match.markForLeveling();
         }

         final boolean advanceAlgorithm = ordersMatchedForCurrentStep == initialQueueSizes[matchStepIndex]
            || (matchStepComparator.getStepIndex(MatchStep.SplitFIFO) == matchStepIndex 
                  && order.getRemainingSplitFIFOQuantity() == 0);
         
         if(advanceAlgorithm) {
            prepareForNextMatchStep(order, matchStepIndex + 1, initialQueueSizes);
            ordersMatchedForCurrentStep = 0;
            matchStepIndex++;
         }
      }

      prepareOrdersForNextAggressor();

		return matches;
   }

   private void prepareForNextMatchStep(Order order, int nextStep, int[] initialQueueSizes) {
      // Take a snapshot of what the initial quantity will be for next step
      order.setInitialQuantityForNextStep();

      // Re-prorate orders WRT current total qty if ProRata step is not first (e.g. Allocation, Configurable)
      if(matchStepComparator.getStepIndex(MatchStep.ProRata) == nextStep) {
         ordersByMatchStep.get(nextStep).forEach(cont -> cont.getOrder().updateProration(totalQuantity)); 
      }

      // If Configurable Algo and next step is SplitFIFO, take a snapshot of the initial SplitFIFO quantity
      if(matchStepComparator.getStepIndex(MatchStep.SplitFIFO) == nextStep) {
         order.setInitialSplitFIFOQuantity();
      }
      
      // If Configurable Algo and next step is Leveling, fill the leveling queue
      if(matchStepComparator.getStepIndex(MatchStep.Leveling) == nextStep) {
         ordersByMatchStep.get(nextStep - 1).stream()
            .filter(cont -> cont.getOrder().isMarkedForLeveling())
            .map(cont -> new OrderContainer(cont.getOrder(), matchStepComparator, nextStep))
            .forEach(o -> ordersByMatchStep.get(nextStep).add(o));
         initialQueueSizes[nextStep] = ordersByMatchStep.get(nextStep).size();
      }
   }

   private int getAggressingQuantity(Order order, Order match, int matchStepIndex) {
      if(matchStepComparator.hasStep(MatchStep.TOP) && match.isTop()) {
         return order.getInitialQuantity();
      }
      if(matchStepComparator.getStepIndex(MatchStep.SplitFIFO) == matchStepIndex) {
         return order.getRemainingSplitFIFOQuantity();
      }
      if(matchingAlgorithm == MatchingAlgorithm.Configurable 
         && matchStepComparator.getStepIndex(MatchStep.ProRata) == matchStepIndex) {
         return (int) Math.floor(order.getCurrentStepInitialQuantity() * match.getProration());
      }
      if(matchStepComparator.getStepIndex(MatchStep.Leveling) == matchStepIndex) {
         return 1;
      }
      if(matchingAlgorithm == MatchingAlgorithm.FIFO) {
         return order.getRemainingQuantity();
      }
      if(matchStepComparator.hasStep(MatchStep.LMM) && match.isLMMAllocatable()) {
			return (int) Math.floor((double) order.getCurrentStepInitialQuantity() 
            * match.getLMMAllocationPercentage() / 100);
      }
      if(matchStepComparator.hasStep(MatchStep.ProRata) && match.isProRataAllocatable()) {
         final int lots = (int) Math.floor(order.getCurrentStepInitialQuantity() * match.getProration());
         return lots >= 2 ? lots : 0;
      }
      return order.getRemainingQuantity();   
   }

   public void prepareOrdersForNextAggressor() {
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

   private void addOrderToProperQueues(Order order) {
      IntStream.range(0, matchStepComparator.getNumberOfSteps())
         .filter(stepIndex -> matchStepComparator.orderFitsStepCriteria(stepIndex, order))
         .forEach(stepIndex -> ordersByMatchStep.get(stepIndex)
            .add(new OrderContainer(order, matchStepComparator, stepIndex)));
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

   private static final class OrderContainer implements Comparable<OrderContainer> {

      private final Order order;
      private final MatchStepComparator matchStepComparator;
      private final int matchStepIndex;

      public OrderContainer(Order order, MatchStepComparator matchStepComparator, int matchStepIndex) {
         this.order = order;
         this.matchStepComparator = matchStepComparator;
         this.matchStepIndex = matchStepIndex;
      }

      public Order getOrder() {
         return order;
      }

      public int getMatchStep() {
         return matchStepIndex;
      }

      @Override
      public int compareTo(OrderContainer other) {
         return matchStepComparator.compare(order, other.getOrder(), matchStepIndex); 
      }
 
   }

}
