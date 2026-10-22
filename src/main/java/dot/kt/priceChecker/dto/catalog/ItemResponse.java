package dot.kt.priceChecker.dto.catalog;

import dot.kt.priceChecker.entities.catalog.Item;
import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.enums.catalog.GadgetType;
import java.util.UUID;

public record ItemResponse(UUID id, ItemDomain category, String brand, String name, String model,
        String series, String applianceType, String size, String features, GadgetType gadgetType) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getCategory(), item.getBrand(), item.getName(), item.getModel(),
                item.getSeries(), item.getApplianceType(), item.getSize(), item.getFeatures(), item.getGadgetType());
    }
}
