import java.util.PriorityQueue;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class OrderBook {

   private final TreeMap<Long, PriceLevel> bids;
   private final TreeMap<Long, PriceLevel> asks;
	private final Map<Integer, PriceLevel> priceLevelByClientOrderId;
   
   public OrderBook() {
      bids = new TreeMap<Long, PriceLevel>(Collections.reverseOrder());
      asks = new TreeMap<Long, PriceLevel>();
      priceLevelByClientOrderId = new HashMap<Integer, PriceLevel>();
   }

   public OrderResponse addOrder(Order order, boolean print) {
		final OrderResponse response = new OrderResponse();
      final TreeMap<Long, PriceLevel> matchAgainst = order.isBuy() ? asks : bids;
      final TreeMap<Long, PriceLevel> resting = order.isBuy() ? bids : asks;

		Optional<PriceLevel> best = Optional.ofNullable(matchAgainst.firstEntry()).map(e -> e.getValue());
      while(best.isPresent() && !order.isFilled()) {
         final boolean isMatch = order.isBuy() ? best.get().getPrice() <= order.getPrice() : best.get().getPrice() >= order.getPrice();
         if(!isMatch) {
            break;
         } 
         final List<MatchEvent> matches = best.get().match(order);
			response.addMatches(best.get().getPrice(), matches);
         if(best.get().getTotalQuantity() == 0) {
            matchAgainst.pollFirstEntry();
         } 
			best = Optional.ofNullable(matchAgainst.firstEntry()).map(e -> e.getValue());
			if(print) {
				matches.forEach(System.out::println);
			}
      }
      
      if(order.isFilled()) {
         return response;
      }
     
		final PriceLevel addTo = resting.computeIfAbsent(order.getPrice(), k -> new PriceLevel(order));
		if(!addTo.hasOrder(order.getClientOrderId())) {
			addTo.add(order);
		}
		priceLevelByClientOrderId.put(order.getClientOrderId(), addTo);

      return response;
   }

	public Order getOrder(int clientOrderId) {
		return priceLevelByClientOrderId.get(clientOrderId).getOrder(clientOrderId);
	}

   public void printBook() {
      System.out.println("============ Bids " + bids.keySet().size() + " ==============");
      bids.entrySet().stream().map(b -> b.getValue().toString()).forEach(System.out::println);
      System.out.println("============ Asks " + asks.keySet().size() + " ==============");
      asks.entrySet().stream().map(a -> a.getValue().toString()).forEach(System.out::println);
   }
   
}
