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
	public int compareTo(PriceLevelKey other) {
		return equals(other) ? 0 : timestamp > ((FIFOPriceLevelKey) other).getTimestamp() ? 1 : -1;
	}

}
