package dot.kt.priceChecker.dto.intel;

import java.time.Instant;
import java.util.UUID;

public record PriceQuote(UUID itemId, String itemName, String currency, Long priceMinor,
        String source, int offerCount, int sellerCount, String notes, Instant updatedAt) { }
