public class FIFOWithLMMPriceLevelKey extends FIFOPriceLevelKey {

	private int allocationPercentage;

	public FIFOWithLMMPriceLevelKey(int orderId, long timestamp, int allocationPercentage) {
		super(orderId, timestamp);
		this.allocationPercentage = allocationPercentage;
	}

	public FIFOWithLMMPriceLevelKey(int orderId) {
		super(orderId);
	}

	public int getAllocationPercentage() {
		return allocationPercentage;
	}

	@Override
	protected int compareToNotEqual(PriceLevelKey other) {
		final FIFOWithLMMPriceLevelKey casted = (FIFOWithLMMPriceLevelKey) other;
		return allocationPercentage > 0 && casted.getAllocationPercentage() == 0 ? 
			-1 : allocationPercentage == 0 && casted.getAllocationPercentage() > 0 ? 
		 	   1 : getTimestamp() < casted.getTimestamp() ? 
				   -1 : 1;
	}

}
