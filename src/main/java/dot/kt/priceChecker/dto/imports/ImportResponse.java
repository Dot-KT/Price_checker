package dot.kt.priceChecker.dto.imports;

import dot.kt.priceChecker.dto.sheets.ParsedInventoryItem;
import dot.kt.priceChecker.dto.sheets.SheetRowError;
import dot.kt.priceChecker.enums.imports.ImportStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportResponse(
        UUID id, ImportStatus status, String sourceUrl, String sheetTitle,
        int totalRows, int validRows, int errorRows,
        List<ParsedInventoryItem> preview, List<SheetRowError> errors,
        Instant createdAt, Instant completedAt) {
}
