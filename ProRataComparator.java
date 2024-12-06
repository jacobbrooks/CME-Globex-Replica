import java.util.Comparator;

public class ProRataComparator extends FIFOComparator {

	@Override
	public int compare(Order a, Order b) {
      if(a.isProRataAllocatable() && b.isProRataAllocatable()) {
         return (int) Math.floor((b.getProration() * 100) - (a.getProration() * 100));
      }
      if(a.isProRataAllocatable() && !b.isProRataAllocatable()) {
         return -1;
      }
      if(b.isProRataAllocatable() && !a.isProRataAllocatable()) {
         return 1;
      }
      return super.compare(a, b);
	}

}
