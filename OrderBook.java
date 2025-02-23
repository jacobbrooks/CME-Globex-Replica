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
   private final PriorityQueue<Order> stopOrders;

   private Optional<Order> currentTopBid;
   private Optional<Order> currentTopAsk;

   private final MatchStepComparator matchStepComparator;
      
   private long lastTradedPrice;
   
   public OrderBook(Security security) {
		this.security = security;
      this.bids = new TreeMap<Long, PriceLevel>(Collections.reverseOrder());
      this.asks = new TreeMap<Long, PriceLevel>();
      this.priceLevelByOrderId = new HashMap<Integer, PriceLevel>();
		this.orderIdByClientOrderId = new HashMap<String, Integer>();
      this.currentTopBid = Optional.empty();
      this.currentTopAsk = Optional.empty();
      this.matchStepComparator = new MatchStepComparator(security.getMatchingAlgorithm());
      this.stopOrders = new PriorityQueue<>(Comparator.comparingLong(Order::getTimestamp));
   }

   public OrderResponse addOrder(Order order, boolean print) {
		final OrderResponse response = new OrderResponse();
      final TreeMap<Long, PriceLevel> matchAgainst = order.isBuy() ? asks : bids;
      final TreeMap<Long, PriceLevel> resting = order.isBuy() ? bids : asks;

		Optional<PriceLevel> best = Optional.ofNullable(matchAgainst.firstEntry()).map(e -> e.getValue());

      final long bestPrice = best.map(b -> b.getPrice()).orElse(this.lastTradedPrice);

      if(order.isStopLimit() || order.isStopWithProtection()) {
         stopOrders.add(order);
         return response;
      }

      while(best.isPresent() && !order.isFilled()) {
         final boolean isMarketMatch = order.isMarketLimit() ||
            (order.isMarketWithProtection() && (order.isBuy() ? 
               best.get().getPrice() < bestPrice + order.getProtectionPoints() :
               best.get().getPrice() > bestPrice - order.getProtectionPoints()));

         final boolean isMatch = isMarketMatch || (order.isBuy() ? 
            best.get().getPrice() <= order.getPrice() : 
            best.get().getPrice() >= order.getPrice());

         if(!isMatch) {
            break;
         } 

         final List<MatchEvent> matches = best.get().match(order);
			response.addMatches(best.get().getPrice(), matches);
         
         this.lastTradedPrice = best.get().getPrice();

         if(best.get().getTotalQuantity() == 0) {
            matchAgainst.pollFirstEntry();
         }

			best = Optional.ofNullable(matchAgainst.firstEntry()).map(e -> e.getValue());

			if(print) {
				matches.forEach(System.out::println);
			} 
      }
    
      if(order.isMarketWithProtection()) {
         order.setPrice(order.isBuy() ? bestPrice + order.getProtectionPoints() : bestPrice - order.getProtectionPoints());
      }

      if(currentTopBid.map(Order::isFilled).orElse(false)) {
         currentTopBid = Optional.empty();
      }

      if(currentTopAsk.map(Order::isFilled).orElse(false)) {
         currentTopAsk = Optional.empty();
      }
      
      if(order.isFilled()) {
         return response;
      }

		final PriceLevel addTo = resting.computeIfAbsent(order.getPrice(), k -> new PriceLevel(order.getPrice(), 
         security.getMatchingAlgorithm(), matchStepComparator));

      final boolean deservesTopStatus = matchStepComparator.hasStep(MatchStep.TOP)
         && order.getPrice() == resting.firstEntry().getKey().longValue()
         && order.getRemainingQuantity() >= security.getTopMin()
         && addTo.isEmpty();
   
      order.setTop(deservesTopStatus);
      addTo.add(order);
		priceLevelByOrderId.put(order.getId(), addTo);
		orderIdByClientOrderId.put(order.getClientOrderId(), order.getId());
      
      if(!deservesTopStatus) {
         return response;
      } 

      if(order.isBuy()) {
         currentTopBid.ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
         currentTopBid = Optional.of(order);
      } else {
         currentTopAsk.ifPresent(o -> priceLevelByOrderId.get(o.getId()).unassignTop());
         currentTopAsk = Optional.of(order);
      }

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

   public void clear() {
      bids.clear();
      asks.clear();
      orderIdByClientOrderId.clear();
      priceLevelByOrderId.clear();
      currentTopBid = Optional.empty();
      currentTopAsk = Optional.empty();
   }

   public void printBook() {
      System.out.println("============ Bids " + bids.keySet().size() + " ==============");
      bids.entrySet().stream().map(b -> b.getValue().toString()).forEach(System.out::println);
      System.out.println("============ Asks " + asks.keySet().size() + " ==============");
      asks.entrySet().stream().map(a -> a.getValue().toString()).forEach(System.out::println);
   }
   
}
