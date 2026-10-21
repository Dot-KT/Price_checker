package dot.kt.priceChecker.dto.imports;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SellerImportTarget(
        @NotNull @Parameter(in = ParameterIn.PATH, required = true) UUID sellerId,
        @NotNull @Parameter(in = ParameterIn.PATH, required = true) UUID id) { }
