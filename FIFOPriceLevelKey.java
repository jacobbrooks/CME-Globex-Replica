public class FIFOPriceLevelKey extends PriceLevelKey {

	private long timestamp;
		
	public FIFOPriceLevelKey(int orderId, long timestamp) {
		super(orderId);
		this.timestamp = timestamp;
	}

	public FIFOPriceLevelKey(int orderId) {
		super(orderId);
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	protected int compareToNotEqual(PriceLevelKey other) {
		return timestamp > ((FIFOPriceLevelKey) other).getTimestamp() ? 1 : -1;
	}

}
