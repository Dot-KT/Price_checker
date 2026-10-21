package dot.kt.priceChecker.dto.sheets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SheetSnapshot(String sourceUrl, String sheetTitle, List<List<Object>> rows) {
    public SheetSnapshot {
        rows = rows.stream()
                .map(row -> Collections.unmodifiableList(new ArrayList<>(row)))
                .toList();
    }
}
