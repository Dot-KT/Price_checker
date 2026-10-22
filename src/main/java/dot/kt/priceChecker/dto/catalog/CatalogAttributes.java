package dot.kt.priceChecker.dto.catalog;

import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.enums.catalog.GadgetType;
import dot.kt.priceChecker.utils.catalog.CatalogText;
import java.util.ArrayList;
import java.util.List;

public record CatalogAttributes(ItemDomain category, String brand, String series,
        String applianceType, String size, String features, GadgetType gadgetType) {
    public CatalogAttributes(ItemDomain category, String brand, String series, String applianceType, String size, String features) {
        this(category, brand, series, applianceType, size, features, category == ItemDomain.GADGET ? GadgetType.PHONE : null);
    }
    public CatalogAttributes {
        brand = CatalogText.brand(brand);
        series = CatalogText.text(series);
        applianceType = CatalogText.text(applianceType);
        size = CatalogText.text(size);
        features = CatalogText.text(features);
    }

    public record Issue(String field, String message) { }

    public List<Issue> errors() {
        List<Issue> errors = new ArrayList<>();
        if (category == null) errors.add(new Issue("category", "Use GADGET or ELECTRICAL_APPLIANCE."));
        if (brand == null) errors.add(new Issue("brand", "Brand is required for a classified item."));
        check(brand, 255, "brand", errors);
        check(series, 255, "series", errors);
        check(applianceType, 255, "appliance_type", errors);
        check(size, 255, "size", errors);
        check(features, 1000, "features", errors);
        if (category == ItemDomain.GADGET) {
            if (gadgetType == null) errors.add(new Issue("gadget_type", "Choose the gadget type, such as PHONE, HEADSET, LAPTOP, or PC."));
            if (gadgetType == GadgetType.PHONE && series == null) errors.add(new Issue("series", "Phone series is required, such as S, A, or iPhone 13."));
            if (applianceType != null || size != null || features != null) {
                errors.add(new Issue("category", "appliance_type, size, and features apply to electrical appliances only."));
            }
        }
        if (category == ItemDomain.ELECTRICAL_APPLIANCE) {
            if (gadgetType != null) errors.add(new Issue("gadget_type", "Gadget type applies to gadgets only."));
            if (applianceType == null) errors.add(new Issue("appliance_type", "Appliance type is required, such as Television or Refrigerator."));
            if (series != null) errors.add(new Issue("series", "Series applies to phones only."));
        }
        return List.copyOf(errors);
    }

    private static void check(String value, int max, String field, List<Issue> errors) {
        if (value != null && value.length() > max) errors.add(new Issue(field, "Must contain at most " + max + " characters."));
        if (value != null && value.indexOf('\0') >= 0) errors.add(new Issue(field, "Must not contain NUL characters."));
    }
}
