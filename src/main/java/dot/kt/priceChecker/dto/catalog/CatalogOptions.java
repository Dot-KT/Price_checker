package dot.kt.priceChecker.dto.catalog;

import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.enums.catalog.GadgetType;
import java.util.List;

public record CatalogOptions(ItemDomain category, String brand, List<String> series,
        List<String> applianceTypes, List<String> sizes, List<String> features, List<GadgetType> gadgetTypes) { }
