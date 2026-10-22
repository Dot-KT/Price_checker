package dot.kt.priceChecker.dto.catalog;

import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.enums.catalog.GadgetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateItemRequest(@NotNull ItemDomain category, @NotBlank @Size(max = 255) String brand,
        @NotBlank @Size(max = 255) String name, @Size(max = 255) String model,
        @Size(max = 255) String series, @Size(max = 255) String applianceType,
        @Size(max = 255) String size, @Size(max = 1000) String features, GadgetType gadgetType) {
    public CatalogAttributes attributes() {
        return new CatalogAttributes(category, brand, series, applianceType, size, features, gadgetType);
    }
}
