package com.cme;

public class MatchEvent {

	private int aggressingOrderId;
	private int restingOrderId;
	private long matchPrice;
	private int matchQuantity;
	private boolean aggressorBuySide;
   private long timestamp;

	public MatchEvent(int aggressingOrderId, int restingOrderId, long matchPrice, int matchQuantity, boolean aggressorBuySide, long timestamp) {
		this.aggressingOrderId = aggressingOrderId;
		this.restingOrderId = restingOrderId;
		this.matchPrice = matchPrice;
		this.matchQuantity = matchQuantity;
		this.aggressorBuySide = aggressorBuySide;
      this.timestamp = timestamp;
	}
   
   public int getRestingOrderId() {
      return restingOrderId;
   }

   public int getMatchQuantity() {
      return matchQuantity;
   }

	public String toString() {
		final String aggressor = aggressorBuySide ? "buy" : "sell";
		final String resting = aggressorBuySide ? "sell" : "buy";
		return "Match: " + aggressor + "(" + aggressingOrderId + ") -> " + resting + "(" + restingOrderId + "), qty=" + matchQuantity + " @" + matchPrice; 
	}	
}
