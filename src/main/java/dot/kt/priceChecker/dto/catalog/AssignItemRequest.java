package dot.kt.priceChecker.dto.catalog;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignItemRequest(@NotNull UUID itemId) { }
