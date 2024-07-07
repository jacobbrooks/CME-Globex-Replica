public class ProRataComparator extends FIFOComparator {

	@Override
	protected int compareNotEqual(Order a, Order b) {
      if(a.isProRataAllocatable() && !b.isProRataAllocatable()) {
         return -1;
      }
      if(b.isProRataAllocatable() && !a.isProRataAllocatable()) {
         return 1;
      }
      if(a.isProRataAllocatable() && b.isProRataAllocatable()) {
         return a.getProration() > b.getProration() ? -1 : 1;
      }
      return super.compareNotEqual(a, b);
	}

}
