public class FIFOComparator extends OrderComparator {

	@Override
	protected int compareNotEqual(Order a, Order b) {
      return compareTimestamps(a, b);
	}

   protected int compareTimestamps(Order a, Order b) {
      return a.getTimestamp() < b.getTimestamp() ? -1 : 1;
   }

}
