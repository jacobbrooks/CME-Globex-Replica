public class Security {
   
   private final int id;
   private final int topMin;
   private final int topMax;
   private final int proRataMin;
   private final int splitPercentage;
	private final MatchingAlgorithm matchingAlgorithm;
   
   public Security(int id, MatchingAlgorithm matchingAlgorithm) {
      this.id = id;
		this.matchingAlgorithm = matchingAlgorithm;
      this.topMin = 0;
      this.topMax = 0;
      this.proRataMin = 2;
      this.splitPercentage = 0;
   }
   
   public Security(int id, MatchingAlgorithm matchingAlgorithm, int topMin, int topMax, 
                                          int proRataMin, int splitPercentage) {
      this.id = id;
		this.matchingAlgorithm = matchingAlgorithm;
      this.topMin = topMin;
      this.topMax = topMax;
      this.splitPercentage = splitPercentage;
      this.proRataMin = proRataMin;
   }

   public Security(int id, MatchingAlgorithm matchingAlgorithm, int topMin, int topMax, int proRataMin) {
      this.id = id;
		this.matchingAlgorithm = matchingAlgorithm;
      this.topMin = topMin;
      this.topMax = topMax;
      this.proRataMin = proRataMin;
      this.splitPercentage = 0;
   }
   
   public int getId() {
      return id;
   }

	public MatchingAlgorithm getMatchingAlgorithm() {
		return matchingAlgorithm;
	}

   public int getTopMin() {
      return topMin;
   }

   public int getTopMax() {
      return topMax;
   }

   public int getProRataMin() {
      return proRataMin;
   }

   public int getSplitPercentage() {
      return splitPercentage;
   }
}
