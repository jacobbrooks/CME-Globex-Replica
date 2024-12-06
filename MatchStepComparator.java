import java.util.stream.IntStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchStepComparator {
   
   private final Map<MatchingAlgorithm, List<MatchStep>> algorithmSteps;
   private final Map<MatchStep, Comparator<Order>> matchStepComparators;

   private static final FIFOComparator fifoComparator = new FIFOComparator();
   private static final LMMComparator lmmComparator = new LMMComparator();
   private static final ProRataComparator proRataComparator = new ProRataComparator();
   private static final TOPComparator topComparator = new TOPComparator();

   private final MatchingAlgorithm matchingAlgorithm;

   public MatchStepComparator(MatchingAlgorithm matchingAlgorithm) {
      this.matchingAlgorithm = matchingAlgorithm;

      algorithmSteps = new HashMap<>();
      algorithmSteps.put(MatchingAlgorithm.FIFO, 
         List.of(MatchStep.FIFO));
      algorithmSteps.put(MatchingAlgorithm.LMM, 
         List.of(MatchStep.LMM, MatchStep.FIFO));
      algorithmSteps.put(MatchingAlgorithm.LMMWithTOP, 
         List.of(MatchStep.TOP, MatchStep.LMM, MatchStep.FIFO));
      algorithmSteps.put(MatchingAlgorithm.ProRata, 
         List.of(MatchStep.ProRata, MatchStep.FIFO));
      algorithmSteps.put(MatchingAlgorithm.Allocation, 
         List.of(MatchStep.TOP, MatchStep.ProRata, MatchStep.FIFO));

      matchStepComparators = new HashMap<>();
      matchStepComparators.put(MatchStep.FIFO, fifoComparator);
      matchStepComparators.put(MatchStep.LMM, lmmComparator);
      matchStepComparators.put(MatchStep.ProRata, proRataComparator);
      matchStepComparators.put(MatchStep.TOP, topComparator);
   }
   
   public boolean orderFitsStepCriteria(int matchStep, Order order) {
      final MatchStep step = algorithmSteps.get(matchingAlgorithm).get(matchStep);
      if(step == MatchStep.FIFO) {
         return true;
      }
      if(step == MatchStep.ProRata) {
         return true;
      }
      if(step == MatchStep.LMM) {
         return order.isLMMAllocatable();
      }
      if(step == MatchStep.TOP) {
         return order.isTop();
      }
      return false;
   }

   public boolean hasStep(MatchStep step) {
      return getStepIndex(step) >= 0;
   }

   public int getStepIndex(MatchStep step) {
      return IntStream.range(0, algorithmSteps.get(matchingAlgorithm).size())
         .filter(i -> algorithmSteps.get(matchingAlgorithm).get(i) == step)
         .findFirst().orElse(-1);
   }

   public int getNumberOfSteps() {
      return algorithmSteps.get(matchingAlgorithm).size();
   }

   public int compare(Order a, Order b, int matchStep) {
      return matchStepComparators.get(algorithmSteps.get(matchingAlgorithm).get(matchStep)).compare(a, b);
   }

}
