public class AllocationComparator extends ProRataComparator {

   @Override
   protected int compareNotEqual(Order a, Order b) {
      return a.isTop() && !b.isTop() ? -1 : b.isTop() && !a.isTop() ? 1 : super.compareNotEqual(a, b);
   }
}
