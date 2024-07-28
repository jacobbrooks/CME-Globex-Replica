import java.util.Comparator;

public class LMMComparator extends FIFOComparator {

	@Override
	public int compare(Order a, Order b) {
		return a.isLMMAllocatable() && !b.isLMMAllocatable() ? 
			-1 : b.isLMMAllocatable() && !a.isLMMAllocatable() ? 
		 	   1 : super.compare(a, b); 
	}

}
