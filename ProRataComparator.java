import java.util.Comparator;

public class ProRataComparator extends FIFOComparator {

	@Override
	public int compare(Order a, Order b) {
      return a.getProration() > b.getProration() ? 
         -1 : a.getProration() < b.getProration() ? 
            1 : super.compare(a, b);
	}

}
