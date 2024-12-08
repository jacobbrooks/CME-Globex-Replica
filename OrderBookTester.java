import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class OrderBookTester {

   private final Security fifo = new Security(1, MatchingAlgorithm.FIFO);
   private final Security lmm = new Security(1, MatchingAlgorithm.LMM);
   private final Security lmmTop = new Security(1, MatchingAlgorithm.LMMWithTOP);
   private final Security proRata = new Security(1, MatchingAlgorithm.ProRata);
   private final Security allocation = new Security(1, MatchingAlgorithm.Allocation);
   private final Security configurable = new Security(1, MatchingAlgorithm.Configurable, 0, 0, 40);
   private final Security configurableNoFIFO = new Security(1, MatchingAlgorithm.Configurable, 0, 0, 0);
   private final Security configurableNoProRata = new Security(1, MatchingAlgorithm.Configurable, 0, 0, 100);

	private final OrderBook fifoOrderBook = new OrderBook(fifo);
	private final OrderBook lmmOrderBook = new OrderBook(lmm);
   private final OrderBook lmmTopOrderBook = new OrderBook(lmmTop);
   private final OrderBook proRataOrderBook = new OrderBook(proRata);
   private final OrderBook allocationOrderBook = new OrderBook(allocation);
   private final OrderBook configurableOrderBook = new OrderBook(configurable);
   private final OrderBook configurableNoFIFOOrderBook = new OrderBook(configurableNoFIFO);
   private final OrderBook configurableNoProRataOrderBook = new OrderBook(configurableNoProRata);

   public boolean testConfigurableNoProRataOrderBook() {
		System.out.println("\ntestConfigurableNoProRataOrderBook()");
		System.out.println("===================================");
      
      // Pairs are {qty, lmmPercentage}
      final List<Order> bids = List.of(new int[]{1, 0}, new int[]{59, 20}, new int[]{40, 10}).stream().map(pair -> {
         hold(10);
         return new Order(Integer.toString(0), configurableNoProRata, true, 100L, pair[0], pair[1]);
      }).toList();      

      bids.forEach(b -> configurableNoProRataOrderBook.addOrder(b, false));
      
      final List<Order> top = bids.stream().filter(Order::isTop).toList();
      final String topOrderId = "Order id: " + top.stream()
         .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

      if(top.size() != 1) {
         printTestFail("TOP event 1: Not exactly 1 top order");
         return false;
      }

      if(!bids.get(0).isTop()) {
         printTestFail("TOP event 1: Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }

      final Order ask = new Order(Integer.toString(0), configurableNoProRata, false, 100L, 50, 0);
      final OrderResponse response = configurableNoProRataOrderBook.addOrder(ask, false);
   
      /*
       * TOP pass - Order 0 is filled for its 1 lot - aggressor qty = 49
       * LMM pass - Order 1 is filled for its LMM allocation 20% * 49 = 9, aggressor qty = 40
       * LMM pass - Order 2 is filled for its LMM allocation 10% * 49 = 4, aggressor qty = 36
       * Split FIFO Pass - Order 1 has 50 remaining qty and is the earliest in the book, and
       *    therefore receives all 100% * 36 remaining aggressor lots
       */ 
      final List<MatchEvent> matches = response.getMatchesByPrice().get(100L);
      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 9, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 4, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 36, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 1", expectedMatches.stream()
            .map(MatchEvent::toString).toList(), matches.stream()
            .map(MatchEvent::toString).toList());
         return false;
      }

		System.out.println("TEST SUCCESS");
      return true;
   }

   public boolean testConfigurableNoFIFOOrderBook() {
		System.out.println("\ntestConfigurableNoFIFOOrderBook()");
		System.out.println("===================================");
      
      // Pairs are {qty, lmmPercentage}
      final List<Order> bids = List.of(new int[]{1, 0}, new int[]{59, 20}, new int[]{40, 10}).stream().map(pair -> {
         hold(10);
         return new Order(Integer.toString(0), configurableNoFIFO, true, 100L, pair[0], pair[1]);
      }).toList();      

      bids.forEach(b -> configurableNoFIFOOrderBook.addOrder(b, false));
      
      final List<Order> top = bids.stream().filter(Order::isTop).toList();
      final String topOrderId = "Order id: " + top.stream()
         .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

      if(top.size() != 1) {
         printTestFail("TOP event 1: Not exactly 1 top order");
         return false;
      }

      if(!bids.get(0).isTop()) {
         printTestFail("TOP event 1: Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }

      final Order ask = new Order(Integer.toString(0), configurableNoFIFO, false, 100L, 50, 0);
      OrderResponse response = configurableNoFIFOOrderBook.addOrder(ask, false);
   
      /*
       * TOP pass - Order 0 is filled for its 1 lot - aggressor qty = 49
       * LMM pass - Order 1 is filled for its LMM allocation 20% * 49 = 9, aggressor qty = 40
       * LMM pass - Order 2 is filled for its LMM allocation 10% * 49 = 4, aggressor qty = 36
       * Split FIFO Pass - 0% aggressor qty is configured for SplitFIFO pass so we move on to ProRata
       * Pro Rata pass - Total resting qty = 86, order 1 has the largest share at 50, so it is allocated
       *    (50 / 86) * 36 = 20 lots
       * Pro Rata pass - Order 2 has the next largest share at 36 so it is allocated
       *    (36 / 86) * 36 = 15 lots, aggressor qty = 1
       * FIFO pass - Order 1 is the earliest order in the book and is assigned the last aggressor lot
       */ 
      final List<MatchEvent> matches = response.getMatchesByPrice().get(100L);
      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 9, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 4, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 20, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 15, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 1, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 1", expectedMatches.stream()
            .map(MatchEvent::toString).toList(), matches.stream()
            .map(MatchEvent::toString).toList());
         return false;
      }

      /*
       * Unrelated to the core objective of this test, but I just want to make sure that if a price level
       * that previously had a TOP order was completely filled, it is eligible to harbor another TOP order
       * (obviously given that no better price level exists).
       */
      final Order oneMoreTOP = new Order(Integer.toString(0), configurableNoFIFO, true, 100L, 1, 0);
      response = configurableNoFIFOOrderBook.addOrder(oneMoreTOP, false);

      if(oneMoreTOP.isTop()) {
         printTestFail("TOP event 2: Not exactly 1 top order");
         return false;
      }

		System.out.println("TEST SUCCESS");
      return true;
   }

   public boolean testConfigurableOrderBook() {
		System.out.println("\ntestConfigurableOrderBook()");
		System.out.println("===================================");
      
      // Pairs are {qty, lmmPercentage}
      List<Order> bids = List.of(new int[]{2, 0}, new int[]{51, 10}, 
         new int[]{47, 20}, new int[]{100, 0}, new int[]{1, 0}, new int[]{1, 0},
         new int[]{1, 0}, new int[]{1, 0}).stream().map(pair -> {
            hold(10);
            return new Order(Integer.toString(0), configurable, true, 100L, pair[0], pair[1]);
         }).toList();      

      bids.forEach(b -> configurableOrderBook.addOrder(b, false));
      
      List<Order> top = bids.stream().filter(Order::isTop).toList();
      String topOrderId = "Order id: " + top.stream()
         .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

      if(top.size() != 1) {
         printTestFail("TOP event 1: Not exactly 1 top order");
         return false;
      }

      if(!bids.get(0).isTop()) {
         printTestFail("TOP event 1: Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }

      Order ask = new Order(Integer.toString(0), configurable, false, 100L, 202, 0);
      OrderResponse response = configurableOrderBook.addOrder(ask, false);
    
      /* 
       * 1. TOP pass - order 0 should match for 2 lots -> aggressor qty = 200
       * 2. LMM pass - 10% LMM order should receive 10% * 200 = 20 lots -> aggressor qty = 180
       * 3. LMM pass - 20% LMM order should receive 20% * 200 = 40 lots -> aggressor qty = 140
       * 4. Split FIFO pass - 40% of the aggressor qty (40% * 140 = 56) should go to the earliest
       *       order in the book (order 1) which only has 31 lots available -> aggressor qty = 109,
       *       remaining splitFIFO qty = 25
       * 5. Split FIFO pass - 7 of 25 Remaining lots are assigned to the next earliest (order 2) 
       *       -> aggressor qty = 102, remaining splitFIFO qty = 18
       * 6. Split FIFO pass - 18 remaining lots are assigned to the next earliest (order 3) 
       *       -> aggressor qty = 84
       * 7. Pro Rata pass - Total resting qty on the book is 86 lots, order 3 has the biggest
       *       proportion with 82 lots, and is filled for 84 * (82 / 86) = 80 lots
       *       -> aggressor qty = 4
       * 8. Pro Rata pass - orders 4,5,6 & 7 all have the next biggest proportions, each with 1 lot, 
       *       84 * (1 / 86) = 0.9767 lots which is rounded down to 0, so they are all marked for leveling
       * 9. Leveling pass - Order 4,5,6 & 7 are assigned 1 lot in that order due to qty/time priority 
       *       -> aggressor qty = 0
       */
      List<MatchEvent> matches = response.getMatchesByPrice().get(100L);
      List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 2, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 20, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 40, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 31, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 7, false, 0L),
         new MatchEvent(ask.getId(), bids.get(3).getId(), 100L, 18, false, 0L),
         new MatchEvent(ask.getId(), bids.get(3).getId(), 100L, 80, false, 0L),
         new MatchEvent(ask.getId(), bids.get(4).getId(), 100L, 1, false, 0L),
         new MatchEvent(ask.getId(), bids.get(5).getId(), 100L, 1, false, 0L),
         new MatchEvent(ask.getId(), bids.get(6).getId(), 100L, 1, false, 0L),
         new MatchEvent(ask.getId(), bids.get(7).getId(), 100L, 1, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 1", expectedMatches.stream()
            .map(MatchEvent::toString).toList(), matches.stream()
            .map(MatchEvent::toString).toList());
         return false;
      }
      
      // Now let's test without LMMs
      bids = List.of(1, 100, 30, 80, 30, 60).stream().map(qty -> {
         hold(10);
         return new Order(Integer.toString(0), configurable, true, 200L, qty, 0);
      }).toList();      


      bids.forEach(b -> configurableOrderBook.addOrder(b, false));
      
      top = bids.stream().filter(Order::isTop).toList();
      topOrderId = "Order id: " + top.stream()
         .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

      if(top.size() != 1) {
         printTestFail("TOP event 2: Not exactly 1 top order - " + top.size());
         return false;
      }

      if(!bids.get(0).isTop()) {
         printTestFail("TOP event 2: Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }

      
      ask = new Order(Integer.toString(0), configurable, false, 200L, 8, 0);
      response = configurableOrderBook.addOrder(ask, false); 
      
      /*
       * 1. TOP Pass - Order 0 is filled for 1 lot
       * 2. Split FIFO Pass - 40% of the remaining aggressor qty of 7 = 3 which all goes to order 1
       * 3. Pro Rata pass - Remaining aggressor qty of 4 * each order's proration leaves orders 1 & 3
       *       being allocated 1 lot, and all other orders (2, 4 & 5) rounded down to 0 and
       *       marked for leveling
       * 4. Leveling pass - 1-lot leveling applies first to order 5 (qty priority), then order 2 (time priority),
       *    but there is no more aggressing qty to fulfill order 4's 1 lot leveling.
       */
      matches = response.getMatchesByPrice().get(200L);
      expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(0).getId(), 200L, 1, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 200L, 3, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 200L, 1, false, 0L),
         new MatchEvent(ask.getId(), bids.get(3).getId(), 200L, 1, false, 0L),
         new MatchEvent(ask.getId(), bids.get(5).getId(), 200L, 1, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 200L, 1, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 2", expectedMatches.stream()
            .map(MatchEvent::toString).toList(), matches.stream()
            .map(MatchEvent::toString).toList());
         return false;
      }

		System.out.println("TEST SUCCESS");
      return true;
   }

   public boolean testAllocationOrderBook() {
		System.out.println("\ntestAllocationOrderBook()");
		System.out.println("===================================");
      
      final List<Order> bids = List.of(2, 56, 42).stream()
         .map(qty -> {
            hold(10);
            return new Order(Integer.toString(0), allocation, true, 100L, qty, 0);
         }).toList();

      bids.forEach(b -> allocationOrderBook.addOrder(b, false));
      
      List<Order> top = bids.stream().filter(Order::isTop).toList();
      final String topOrderId = "Order id: " + top.stream()
         .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

      if(top.size() != 1) {
         printTestFail("Not exactly 1 top order");
         return false;
      }

      if(!bids.get(0).isTop()) {
         printTestFail("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }

      Order ask = new Order(Integer.toString(0), allocation, false, 100L, 50, 0);
      OrderResponse response = allocationOrderBook.addOrder(ask, false);
      
      List<MatchEvent> matches = response.getMatchesByPrice().get(100L);
      List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 2, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 27, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 20, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 1, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 1", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }

      // There should now be 50 resting quantity on the book, let's hit it with one more aggressor
      ask = new Order(Integer.toString(0), allocation, false, 100L, 50, 0);
      response = allocationOrderBook.addOrder(ask, false);
      matches = response.getMatchesByPrice().get(100L);
      expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 28, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 22, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 2", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }
      
		System.out.println("TEST SUCCESS");
      return true;
   }

   public boolean testProRataOrderBook() {
		System.out.println("\ntestProRataOrderBook()");
		System.out.println("===================================");
      
      final List<Order> bids = List.of(2, 42, 56).stream()
         .map(qty -> {
            hold(10);
            return new Order(Integer.toString(0), proRata, true, 100L, qty, 0);
         }).toList();

      bids.forEach(b -> proRataOrderBook.addOrder(b, false));

      Order ask = new Order(Integer.toString(0), proRata, false, 100L, 50, 0);
      OrderResponse response = proRataOrderBook.addOrder(ask, false);
      
      List<MatchEvent> matches = response.getMatchesByPrice().get(100L);
      List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 28, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 21, false, 0L),
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 1", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }
      
      ask = new Order(Integer.toString(0), proRata, false, 100L, 50, 0);
      response = proRataOrderBook.addOrder(ask, false);
      
      matches = response.getMatchesByPrice().get(100L);
      expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 28, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 21, false, 0L),
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L)
      );
      
      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 2", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }
        
		System.out.println("TEST SUCCESS");
      return true;
   }

   public boolean testLMMWithTopOrderBook() {
		System.out.println("\ntestLMMWithTopOrderBook()");
		System.out.println("===================================");
   
      // Top order should be the first to market	
      final List<Order> bids = List.of(0, 10).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmmTop, true, 100L, 10, p);
         }).toList();

      bids.forEach(b -> lmmTopOrderBook.addOrder(b, false));

      List<Order> top = bids.stream().filter(Order::isTop).toList();
      final String topOrderId = "Order id: " + top.stream()
         .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

      if(top.size() != 1) {
         printTestFail("Not exactly 1 top order");
         return false;
      }

      if(!bids.get(0).isTop()) {
         printTestFail("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }

      /*
       * First to best the market should be the new top, so the first order at this 200 
       * price level should dethrone the original top order
       */
      final List<Order> higherBids = List.of(0, 10, 20, 0).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmmTop, true, 200L, 10, p);
         }).toList();
      
      higherBids.forEach(o -> {
         lmmTopOrderBook.addOrder(o, false);
      });
      
      top = Stream.concat(bids.stream().filter(Order::isTop), higherBids.stream().filter(Order::isTop)).toList();
      if(top.size() != 1) {
         printTestFail("Not exactly 1 top order");
         return false;
      }

      if(!higherBids.get(0).isTop() || bids.get(0).isTop()) {
         printTestFail("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }
     
      final Order ask = new Order(Integer.toString(0), lmmTop, false, 200L, 40, 0);
     
      /* 
       * We expect the $200x40 ask to first match against the TOP order for 10 lots. (30 lots remain)
       * 
       * The next two orders are from LMMs and are both guaranteed their LMM allocations, so it doesn't
       * matter who is filled first. We break the tie by whoever is earliest in the book, which is:
       * 
       * the 10% LMM order fot its LMM allocation percentage (10% * 30 lots = 3 lots), then
       * the 20% LMM order for its LMM allocation percentage (20% * 30 lots = 6 lots)
       *
       * We then FIFO match against the 10% LMM order for 7 remaining lots since it's the earliest remaining, 
       * then a FIFO match against the 20% LMM order for 4 remaining lots since it's the next earliest remaining, 
       *
       * then lastly a FIFO match the last order for the all 10 lots.
       */
      final OrderResponse response = lmmTopOrderBook.addOrder(ask, false);
      List<MatchEvent> matches = response.getMatchesByPrice().get(200L);
      List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), higherBids.get(0).getId(), 200L, 10, false, 0L),
         new MatchEvent(ask.getId(), higherBids.get(1).getId(), 200L, 3, false, 0L),
         new MatchEvent(ask.getId(), higherBids.get(2).getId(), 200L, 6, false, 0L),
         new MatchEvent(ask.getId(), higherBids.get(1).getId(), 200L, 7, false, 0L),
         new MatchEvent(ask.getId(), higherBids.get(2).getId(), 200L, 4, false, 0L),
         new MatchEvent(ask.getId(), higherBids.get(3).getId(), 200L, 10, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }
        
		System.out.println("TEST SUCCESS");
      return true;
   }
   
   public boolean testLMMOrderBookOneLeft() {
		System.out.println("\ntestLMMOrderBookOneLeft()");
		System.out.println("===================================");
      
      lmmOrderBook.clear();

		final List<Order> bids = List.of(50, 60).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmm, true, 100L, 10, p);
         }).toList();

      bids.forEach(o -> {
         lmmOrderBook.addOrder(o, false);
      });

      final Order ask = new Order(Integer.toString(0), lmm, false, 100L, 1, 0);
      final OrderResponse response = lmmOrderBook.addOrder(ask, false);

      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 1, false, 0L)
      );

      final List<MatchEvent> matches = response.getMatchesByPrice().get(100L);
      final boolean success = matches.size() == expectedMatches.size() 
         && !IntStream.range(0, matches.size())
               .anyMatch(i -> matches.get(i).getRestingOrderId() != expectedMatches.get(i).getRestingOrderId() 
                  || matches.get(i).getMatchQuantity() != expectedMatches.get(i).getMatchQuantity());

      if(!success) {
         printTestFail("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }

		System.out.println("TEST SUCCESS");
      return true;
   }   

   public boolean testLMMWithTopOrderBookMultipleAggressors() {
		System.out.println("\ntestLMMWithTopOrderBookMultipleAggressors()");
		System.out.println("==========================");
      
      lmmTopOrderBook.clear();

		final List<Order> bids = List.of(0, 20, 80).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmmTop, true, 100L, 100, p);
         }).toList();

      bids.forEach(o -> {
         lmmTopOrderBook.addOrder(o, false);
      });

      Order ask = new Order(Integer.toString(0), lmmTop, false, 100L, 30, 0);
      OrderResponse response = lmmTopOrderBook.addOrder(ask, false);

      // Top order should snag all the quantity of the aggressor leaving none for the LMMs
      List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 30, false, 0L)
      );
      List<MatchEvent> matches = response.getMatchesByPrice().get(100L);

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 1", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }

      // Since top order was not completely filled, it should still be top
      List<Order> top = bids.stream().filter(Order::isTop).toList();
      final String topOrderId = "Order id: " + top.stream()
         .map(o -> Integer.toString(o.getId())).findAny().orElse("none");

      if(top.size() != 1) {
         printTestFail("Not exactly 1 top order");
         return false;
      }

      if(!bids.get(0).isTop()) {
         printTestFail("Top order", List.of("Order id: " + bids.get(0).getId()), List.of(topOrderId));
         return false;
      }

      /* 
       * Since top order was not completely filled (70 lots left), it should 
       * still be considered top and snag all the quantity from the next aggressor
       */
      ask = new Order(Integer.toString(0), lmmTop, false, 100L, 30, 0);
      response = lmmTopOrderBook.addOrder(ask, false);
      
      expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 30, false, 0L)
      );
      matches = response.getMatchesByPrice().get(100L);
      
      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 2", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }

		System.out.println("TEST SUCCESS");
      return true;
   }

   public boolean testLMMOrderBookMultipleAggressors() {
		System.out.println("\ntestLMMOrderBookMultipleAggressors()");
		System.out.println("==========================");
      
      lmmOrderBook.clear();

		final List<Order> bids = List.of(0, 20, 80).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmm, true, 100L, 100, p);
         }).toList();

      bids.forEach(o -> {
         lmmOrderBook.addOrder(o, false);
      });

      Order ask = new Order(Integer.toString(0), lmm, false, 100L, 30, 0);
      OrderResponse response = lmmOrderBook.addOrder(ask, false);

      List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 6, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 24, false, 0L)
      );
      List<MatchEvent> matches = response.getMatchesByPrice().get(100L);

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 1", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }
      
      ask = new Order(Integer.toString(0), lmm, false, 100L, 30, 0);
      response = lmmOrderBook.addOrder(ask, false);
      
      expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 6, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 24, false, 0L)
      );
      matches = response.getMatchesByPrice().get(100L);
      
      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches 2", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }

		System.out.println("TEST SUCCESS");
      return true;
   }

   public boolean testLMMOrderBook() {
		System.out.println("\ntestLMMOrderBook()");
		System.out.println("==========================");
      
      lmmOrderBook.clear();

		final List<Order> bids = List.of(0, 20, 80).stream()
         .map(p -> {
            hold(10);
            return new Order(Integer.toString(0), lmm, true, 100L, 10, p);
         }).toList();

      bids.forEach(o -> {
         lmmOrderBook.addOrder(o, false);
      });

      final Order ask = new Order(Integer.toString(0), lmm, false, 100L, 30, 0);
      final OrderResponse response = lmmOrderBook.addOrder(ask, false);

      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 6, false, 0L),
         new MatchEvent(ask.getId(), bids.get(2).getId(), 100L, 10, false, 0L),
         new MatchEvent(ask.getId(), bids.get(0).getId(), 100L, 10, false, 0L),
         new MatchEvent(ask.getId(), bids.get(1).getId(), 100L, 4, false, 0L)
      );
      final List<MatchEvent> matches = response.getMatchesByPrice().get(100L);

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
         return false;
      }

		System.out.println("TEST SUCCESS");
      return true;
   }

	public boolean testFIFOOrderBook() {
		System.out.println("\ntestFIFOOrderBook()");
		System.out.println("====================");

		// 5 bid price levels, with the 200 price level having multiple orders (to test time priority)
		final List<Long> bidPrices = List.of(100L, 150L, 200L, 200L, 250L, 300L);
      final List<Long> askPrices = List.of(100L, 150L, 200L, 250L, 300L);

      final List<Order> bids = bidPrices.stream()
         .map(p -> {
			   hold(10); // So that each order has slightly different timestamps
            return new Order(Integer.toString(0), fifo, true, p, 10, 0);
         }).toList();

      final List<Order> asks = askPrices.stream()
         .map(p -> {
			   hold(10); // So that each order has slightly different timestamps
            return new Order(Integer.toString(0), fifo, false, p, 10, 0);
         }).toList();

		// Add bids
		bids.forEach(b -> {
			fifoOrderBook.addOrder(b, false);
		});
		
		final List<OrderResponse> responses = asks.stream().map(a -> {
			return fifoOrderBook.addOrder(a, false);
		}).toList();

      final List<MatchEvent> matches = responses.stream()
         .map(r -> r.getMatchesByPrice().entrySet().stream().map(e -> e.getValue()).findFirst())
         .filter(opt -> opt.isPresent())
         .map(opt -> opt.get().get(0))
         .toList();
      
      final List<MatchEvent> expectedMatches = List.of(
         new MatchEvent(asks.get(0).getId(), bids.get(5).getId(), 300L, 10, false, 0L),
         new MatchEvent(asks.get(1).getId(), bids.get(4).getId(), 250L, 10, false, 0L),
         new MatchEvent(asks.get(2).getId(), bids.get(2).getId(), 200L, 10, false, 0L)
      );

      if(!equalMatches(expectedMatches, matches)) {
         printTestFail("matches", expectedMatches.stream().map(MatchEvent::toString).toList(), matches.stream().map(MatchEvent::toString).toList());
      }
      
		/*
		 * Resulting order book state should be bids: {200, 150, 100}, asks: {250, 300}
		 */
		final List<Long> expectedBids = List.of(200L, 150L, 100L);
		final List<Long> expectedAsks = List.of(250L, 300L);
		final List<Long> actualBids = fifoOrderBook.getBidPrices();
		final List<Long> actualAsks = fifoOrderBook.getAskPrices();

		if(!(actualBids.size() == expectedBids.size() && actualAsks.size() == expectedAsks.size())) {
			printTestFail("bids", expectedBids.stream().map(l -> l.toString()).toList(), actualBids.stream().map(l -> l.toString()).toList());
			printTestFail("asks", expectedAsks.stream().map(l -> l.toString()).toList(), actualAsks.stream().map(l -> l.toString()).toList());
			return false;
		}

		if(IntStream.range(0, expectedBids.size()).anyMatch(i -> expectedBids.get(i) != actualBids.get(i).longValue())) {
			printTestFail("bids", expectedBids.stream().map(l -> l.toString()).toList(), actualBids.stream().map(l -> l.toString()).toList());
			printTestFail("asks", expectedAsks.stream().map(l -> l.toString()).toList(), actualAsks.stream().map(l -> l.toString()).toList());
			return false;
		}
	
		if(IntStream.range(0, expectedAsks.size()).anyMatch(i -> expectedAsks.get(i) != actualAsks.get(i).longValue())) {
			printTestFail("bids", expectedBids.stream().map(l -> l.toString()).toList(), actualBids.stream().map(l -> l.toString()).toList());
			printTestFail("asks", expectedAsks.stream().map(l -> l.toString()).toList(), actualAsks.stream().map(l -> l.toString()).toList());
			return false;
		}

		System.out.println("TEST SUCCESS");
		return true;
	}

   private void printTestFail(String message) {
      System.out.println("TEST FAIL");
      System.out.println("------------------------------------");
      System.out.println(message);
   }

	private void printTestFail(String criteria, List<String> expected, List<String> actual) {
      System.out.println("TEST FAIL");
      System.out.println("------------------------------------");
		System.out.println("Expected " + criteria + ": [");
		expected.forEach(b -> System.out.println("  " + b + ","));
		System.out.println("], \nActual " + criteria + ": [");
		actual.forEach(b -> System.out.println("  " + b + ", "));
		System.out.print("]\n");
		System.out.println();
	} 
   
   private boolean equalMatches(List<MatchEvent> expected, List<MatchEvent> actual) {
      return actual.size() == expected.size() 
         && !IntStream.range(0, actual.size())
               .anyMatch(i -> actual.get(i).getRestingOrderId() != expected.get(i).getRestingOrderId() 
                  || actual.get(i).getMatchQuantity() != expected.get(i).getMatchQuantity());
   }

	private void hold(long ms) {
		try {Thread.sleep(ms);} catch(InterruptedException e) {e.printStackTrace();}
	}

}
