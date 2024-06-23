import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class OrderGateway {

	final PriorityBlockingQueue<Order> orderQueue = new PriorityBlockingQueue<>(1, Comparator.comparing(Order::getTimestamp));
	final Map<Integer, OrderBook> orderBooks = new ConcurrentHashMap<>(); 
	final Map<Integer, OrderResponse> responses = new ConcurrentHashMap<>();
	final Map<Integer, Security> securitiesById = new HashMap<>();

	public OrderGateway() {
		securitiesById.put(1, new Security(1, MatchingAlgorithm.FIFO));
		securitiesById.put(2, new Security(2, MatchingAlgorithm.LMM));
	}

	public void start() {
		Runnable queueConsumer = () -> {
			while(true) {
				final Order nextOrder = orderQueue.poll();
				if(nextOrder == null) {
					continue;
				}
				final OrderBook book = orderBooks.computeIfAbsent(nextOrder.getSecurity().getId(), k -> new OrderBook(securitiesById.get(nextOrder.getSecurity().getId())));
				OrderResponse response = book.addOrder(nextOrder, true);
				responses.put(nextOrder.getId(), response);
			}
      };
		new Thread(queueConsumer).start();
	}

	public void submit(Order order) {
		orderQueue.put(order);
	}

	public void printBook(int securityId) {
		orderBooks.get(securityId).printBook();
	}

}
