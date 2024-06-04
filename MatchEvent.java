public class MatchEvent {

	private int aggressingOrderId;
	private int restingOrderId;
	private long matchPrice;
	private long matchQuantity;
	private boolean aggressorBuySide;

	public MatchEvent(int aggressingOrderId, int restingOrderId, long matchPrice, long matchQuantity, boolean aggressorBuySide) {
		this.aggressingOrderId = aggressingOrderId;
		this.restingOrderId = restingOrderId;
		this.matchPrice = matchPrice;
		this.matchQuantity = matchQuantity;
		this.aggressorBuySide = aggressorBuySide;
	}

	public String toString() {
		final String aggressor = aggressorBuySide ? "buy" : "sell";
		final String resting = aggressorBuySide ? "sell" : "buy";
		return "Match: " + aggressor + "(" + aggressingOrderId + ") -> " + resting + "(" + restingOrderId + "), qty=" + matchQuantity + " @" + matchPrice; 
	}	
}
