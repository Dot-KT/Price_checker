package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.dto.telegram.ProductSource;
import java.util.List;

public interface ProductSourceLookup {
    List<ProductSource> find(String model, int productIndex);
}
