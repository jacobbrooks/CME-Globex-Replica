public class LMMComparator extends FIFOComparator {

   protected int compareNotEqualLMM(Order a, Order b) {
		return a.getAllocationPercentage() > 0 && b.getAllocationPercentage() == 0 ? 
			-1 : a.getAllocationPercentage() == 0 && b.getAllocationPercentage() > 0 ? 
		 	   1 : compareTimestamps(a, b);
   }

	@Override
	protected int compareNotEqual(Order a, Order b) {
      return compareNotEqualLMM(a, b);
	}

}
