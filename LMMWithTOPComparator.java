public class LMMWithTOPComparator extends LMMComparator {

	@Override
	protected int compareNotEqual(Order a, Order b) {
		return a.isTop() ? -1 : b.isTop() ? 1 : compareNotEqualLMM(a, b); 
	}

}
