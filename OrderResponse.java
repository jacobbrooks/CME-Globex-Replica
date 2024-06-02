import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderResponse {
	private final Map<Long, List<MatchEvent>> matchesByPrice;

	public OrderResponse() {
		matchesByPrice = new HashMap<Long, List<MatchEvent>>();
	}

	public void addMatches(long price, List<MatchEvent> matchEvents) {
		matchesByPrice.put(price, matchEvents);
	}

	public Map<Long, List<MatchEvent>> getMatchesByPrice() {
		return matchesByPrice;
	}

	public boolean isEmpty() {
		return matchesByPrice.isEmpty();
	}
}
