public abstract class PriceLevelKey implements Comparable<PriceLevelKey> {

	private int orderId;

	public PriceLevelKey(int orderId) {
		this.orderId = orderId;
	}

	public int getOrderId() {
		return orderId;
	}

	@Override
	public boolean equals(Object other) {
		return other != null && (other instanceof PriceLevelKey) && ((PriceLevelKey) other).getOrderId() == orderId;
	}

	@Override
	public int hashCode() {
		return orderId;
	}

	@Override
	public abstract int compareTo(PriceLevelKey other);
}

