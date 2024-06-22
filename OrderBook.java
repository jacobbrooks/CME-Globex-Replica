import java.util.stream.Collectors;
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

	private final Security security;
   private final TreeMap<Long, PriceLevel> bids;
   private final TreeMap<Long, PriceLevel> asks;
	private final Map<Integer, PriceLevel> priceLevelByOrderId;
	private final Map<String, Integer> orderIdByClientOrderId;
   private Order currentTop;
   
   public OrderBook(Security security) {
		this.security = security;
      this.bids = new TreeMap<Long, PriceLevel>(Collections.reverseOrder());
      this.asks = new TreeMap<Long, PriceLevel>();
      this.priceLevelByOrderId = new HashMap<Integer, PriceLevel>();
		this.orderIdByClientOrderId = new HashMap<String, Integer>();
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

		final PriceLevel addTo = resting.computeIfAbsent(order.getPrice(), k -> new PriceLevel(order, security.getMatchingAlgorithm()));
		if(!addTo.hasOrder(order.getOrderId())) {
         // Price level already existed
			addTo.add(order);
		} else if(security.getMatchingAlgorithm() == MatchingAlgorithm.LMMWithTOP) {
         // We created a new price level
         final boolean deservesTopStatus = order.getPrice() == resting.firstEntry().getKey().longValue()
            && order.getRemainingQuantity() >= security.getTopMin();
         if(deservesTopStatus) {
            Optional.ofNullable(priceLevelByOrderId.get(currentTop.getOrderId()))
               .ifPresent(p -> p.assignTop(currentTop, false));
            addTo.assignTop(order, true);
         }
      }
		priceLevelByOrderId.put(order.getOrderId(), addTo);
		orderIdByClientOrderId.put(order.getClientOrderId(), order.getOrderId());

      return response;
   }
   
	public Order getOrder(String clientOrderId) {
		final int orderId = orderIdByClientOrderId.get(clientOrderId);
		return priceLevelByOrderId.get(orderId).getOrder(orderId);
	}

	public List<Long> getBidPrices() {
		return bids.keySet().stream().collect(Collectors.toList());
	}
	
	public List<Long> getAskPrices() {
		return asks.keySet().stream().collect(Collectors.toList());
	}

   public void printBook() {
      System.out.println("============ Bids " + bids.keySet().size() + " ==============");
      bids.entrySet().stream().map(b -> b.getValue().toString()).forEach(System.out::println);
      System.out.println("============ Asks " + asks.keySet().size() + " ==============");
      asks.entrySet().stream().map(a -> a.getValue().toString()).forEach(System.out::println);
   }
   
}
