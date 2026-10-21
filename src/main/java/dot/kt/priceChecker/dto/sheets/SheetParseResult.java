package dot.kt.priceChecker.dto.sheets;

import java.util.List;

public record SheetParseResult(List<ParsedInventoryItem> items, List<SheetRowError> errors, int totalRows) {
    public SheetParseResult {
        items = List.copyOf(items);
        errors = List.copyOf(errors);
    }

    public boolean hasErrors() { return !errors.isEmpty(); }
}
