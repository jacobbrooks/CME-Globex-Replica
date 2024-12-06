import java.util.Comparator;

public class TOPComparator implements Comparator<Order> {

   @Override
   public int compare(Order a, Order b) {
      return a.isTop() && !b.isTop() ? -1 : b.isTop() && !a.isTop() ? 1 : 0;
   }
}
