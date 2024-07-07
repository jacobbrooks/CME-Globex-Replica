public class LMMComparator extends FIFOComparator {

	@Override
	protected int compareNotEqual(Order a, Order b) {
		return a.isLMMAllocatable() && !b.isLMMAllocatable() ? 
			-1 : b.isLMMAllocatable() && !a.isLMMAllocatable() ? 
		 	   1 : super.compareNotEqual(a, b);
	}

}
