public class LMMWithTOPPriceLevelKey extends LMMPriceLevelKey {

   final boolean top;

	public LMMWithTOPPriceLevelKey(int orderId, long timestamp, int allocationPercentage, boolean top) {
		super(orderId, timestamp, allocationPercentage);
      this.top = top;
	}

	public LMMWithTOPPriceLevelKey(int orderId) {
		super(orderId);
      this.top = false;
	}

   public boolean isTop() {
      return top;
   }

	@Override
	protected int compareToNotEqual(PriceLevelKey other) {
		final LMMWithTOPPriceLevelKey casted = (LMMWithTOPPriceLevelKey) other;
		return top ? -1 : casted.isTop() ? 1 : compareToNotEqualLMM(other); 
	}

}
