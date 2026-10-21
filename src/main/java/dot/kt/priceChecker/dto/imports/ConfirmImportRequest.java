package dot.kt.priceChecker.dto.imports;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ConfirmImportRequest(
        @NotNull @AssertTrue(message = "Set confirm to true after reviewing the import preview.") Boolean confirm) {
}
