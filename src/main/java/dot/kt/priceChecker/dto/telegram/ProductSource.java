package dot.kt.priceChecker.dto.telegram;

import java.time.Instant;

public record ProductSource(String id, int productIndex, String title, String url, String text, Instant checkedAt,
        java.util.Map<String, String> specifications) {
    public ProductSource(String id, int productIndex, String title, String url, String text, Instant checkedAt) {
        this(id, productIndex, title, url, text, checkedAt, java.util.Map.of());
    }
}
