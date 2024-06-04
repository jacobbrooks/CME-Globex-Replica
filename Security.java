public class Security {
   
   private final int id;
	private MatchingAlgorithm matchingAlgorithm;
   
   public Security(int id, MatchingAlgorithm matchingAlgorithm) {
      this.id = id;
		this.matchingAlgorithm = matchingAlgorithm;
   }
   
   public int getId() {
      return id;
   }

	public MatchingAlgorithm getMatchingAlgorithm() {
		return matchingAlgorithm;
	}
}
