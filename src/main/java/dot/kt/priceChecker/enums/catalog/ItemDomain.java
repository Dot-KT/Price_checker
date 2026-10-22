package dot.kt.priceChecker.enums.catalog;

import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ItemDomain {
    GADGET("Gadgets"),
    ELECTRICAL_APPLIANCE("Electrical appliances");

    private final String label;

    ItemDomain(String label) { this.label = label; }
    public String label() { return label; }

    public static ItemDomain fromSheet(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_")) {
            case "gadget", "gadgets", "phone", "phones" -> GADGET;
            case "electrical_appliance", "electrical_appliances", "electric_appliance", "electric_appliances",
                    "appliance", "appliances" -> ELECTRICAL_APPLIANCE;
            default -> throw new IllegalArgumentException("Use GADGET or ELECTRICAL_APPLIANCE.");
        };
    }

    @JsonCreator
    public static ItemDomain fromJson(String value) { return fromSheet(value); }
}
