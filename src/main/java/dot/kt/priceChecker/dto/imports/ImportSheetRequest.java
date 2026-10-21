package dot.kt.priceChecker.dto.imports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImportSheetRequest(@NotBlank @Size(max = 2048) String url) {
}
