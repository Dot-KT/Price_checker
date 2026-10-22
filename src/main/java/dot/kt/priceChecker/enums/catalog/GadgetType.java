package dot.kt.priceChecker.enums.catalog;

import java.util.Locale;

public enum GadgetType {
    PHONE, HEADSET, LAPTOP, PC, TABLET, SMARTWATCH, OTHER;

    public static GadgetType fromSheet(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_")) {
            case "phone", "phones", "smartphone", "smartphones" -> PHONE;
            case "headset", "headsets", "headphone", "headphones", "earbuds" -> HEADSET;
            case "laptop", "laptops" -> LAPTOP;
            case "pc", "desktop", "desktops", "computer", "computers" -> PC;
            case "tablet", "tablets" -> TABLET;
            case "smartwatch", "smartwatches", "watch" -> SMARTWATCH;
            case "other" -> OTHER;
            default -> throw new IllegalArgumentException("Choose PHONE, HEADSET, LAPTOP, PC, TABLET, SMARTWATCH, or OTHER.");
        };
    }
}
