public abstract class PriceLevelKey implements Comparable<PriceLevelKey> {

	protected final int orderId;

	public PriceLevelKey(int orderId) {
		this.orderId = orderId;
	}

	public int getOrderId() {
		return orderId;
	}

	@Override
	public boolean equals(Object other) {
		return other != null && getClass() == other.getClass() && ((PriceLevelKey) other).getOrderId() == orderId;
	}

	@Override
	public int hashCode() {
		return orderId;
	}

	@Override
	public int compareTo(PriceLevelKey other) {
		return equals(other) ? 0 : compareToNotEqual(other);
	}

	protected abstract int compareToNotEqual(PriceLevelKey other);
}

