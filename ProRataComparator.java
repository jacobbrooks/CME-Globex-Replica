public class ProRataComparator extends FIFOComparator {

	@Override
	protected int compareNotEqual(Order a, Order b) {
      return a.getProration() <= b.getProration() ? -1 : 1;
	}

}
