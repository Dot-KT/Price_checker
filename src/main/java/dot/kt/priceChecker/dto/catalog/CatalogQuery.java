package dot.kt.priceChecker.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import dot.kt.priceChecker.enums.catalog.GadgetType;

public record CatalogQuery(@NotBlank @Size(max = 255) String brand, @Size(max = 255) String series,
        @Size(max = 255) String applianceType, @Size(max = 255) String applianceSize,
        @Size(max = 1000) String feature, @Size(max = 255) String q, GadgetType gadgetType) { }
