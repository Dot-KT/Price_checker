package dot.kt.priceChecker.dto.sellers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveSellerSheetRequest(@NotBlank @Size(max = 2048) String url) {
}
