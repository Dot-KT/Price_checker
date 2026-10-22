package dot.kt.priceChecker.dto.inventory;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InventoryItemTarget(@NotNull @Parameter(in = ParameterIn.PATH, required = true) UUID id) { }
