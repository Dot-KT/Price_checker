package dot.kt.priceChecker.dto.intel;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record SavePriceIntelRequest(@NotNull @DecimalMin("0") BigDecimal price,
        @NotBlank @Size(min = 3, max = 3) String currency, @Size(max = 2000) String notes) { }
