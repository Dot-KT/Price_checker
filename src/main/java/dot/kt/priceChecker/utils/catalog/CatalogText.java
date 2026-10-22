package dot.kt.priceChecker.utils.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Collectors;

public final class CatalogText {
    private CatalogText() { }

    public static String text(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip().replaceAll("\\s+", " ");
    }

    public static String key(String value) {
        String text = text(value);
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    public static String brand(String value) {
        return switch (key(value)) {
            case "apple", "iphone" -> "Apple";
            case "samsung" -> "Samsung";
            case "lg", "life is good", "life's good", "life’s good" -> "LG";
            case "hisense" -> "Hisense";
            case "itel" -> "itel";
            case "oraimo" -> "oraimo";
            default -> text(value);
        };
    }

    public static String identity(String... parts) {
        String normalized = Arrays.stream(parts).map(CatalogText::key).collect(Collectors.joining("\u0000"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
