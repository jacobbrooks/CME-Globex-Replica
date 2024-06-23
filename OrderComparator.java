import java.util.Comparator;

public abstract class OrderComparator implements Comparator<Order> {

	@Override
	public int compare(Order a, Order b) {
		return a.getId() == b.getId() ? 0 : compareNotEqual(a, b);
	}

	protected abstract int compareNotEqual(Order a, Order b);
}

