public class LMMComparator extends FIFOComparator {

   protected int compareNotEqualLMM(Order a, Order b) {
		return a.isLMMAllocatable() && !b.isLMMAllocatable() ? 
			-1 : b.isLMMAllocatable() && !a.isLMMAllocatable() ? 
		 	   1 : compareTimestamps(a, b);
   }

	@Override
	protected int compareNotEqual(Order a, Order b) {
      return compareNotEqualLMM(a, b);
	}

}
