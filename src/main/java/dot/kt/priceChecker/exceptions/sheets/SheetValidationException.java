package dot.kt.priceChecker.exceptions.sheets;

import dot.kt.priceChecker.dto.sheets.SheetRowError;
import dot.kt.priceChecker.exceptions.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class SheetValidationException extends ApiException {
    private final UUID importId;
    private final List<SheetRowError> errors;

    public SheetValidationException(UUID importId, List<SheetRowError> errors) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "SHEET_VALIDATION_FAILED",
                "Fix the sheet errors and save the link again. The saved link and inventory have not changed.");
        this.importId = importId;
        this.errors = List.copyOf(errors);
    }

    public UUID getImportId() { return importId; }
    public List<SheetRowError> getErrors() { return errors; }
}
