package dot.kt.priceChecker.service.sheets;

import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
public interface SheetReader {
    SheetSnapshot read(String url);
}
