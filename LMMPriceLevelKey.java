public class LMMPriceLevelKey extends FIFOPriceLevelKey {

	private int allocationPercentage;

	public LMMPriceLevelKey(int orderId, long timestamp, int allocationPercentage) {
		super(orderId, timestamp);
		this.allocationPercentage = allocationPercentage;
	}

	public LMMPriceLevelKey(int orderId) {
		super(orderId);
	}

	public int getAllocationPercentage() {
		return allocationPercentage;
	}

   protected int compareToNotEqualLMM(PriceLevelKey other) {
		final LMMPriceLevelKey casted = (LMMPriceLevelKey) other;
		return allocationPercentage > 0 && casted.getAllocationPercentage() == 0 ? 
			-1 : allocationPercentage == 0 && casted.getAllocationPercentage() > 0 ? 
		 	   1 : getTimestamp() < casted.getTimestamp() ? 
				   -1 : 1;
   }

	@Override
	protected int compareToNotEqual(PriceLevelKey other) {
      return compareToNotEqualLMM(other);
	}

}
