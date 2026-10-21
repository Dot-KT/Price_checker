package dot.kt.priceChecker.dto.sellers;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SellerTarget(@NotNull @Parameter(in = ParameterIn.PATH, required = true) UUID sellerId) { }
