package dot.kt.priceChecker.dto.telegram;

import java.util.UUID;

public record BotOffer(UUID id, UUID itemId, String itemName, String sellerName, String model,
        String condition, String description, long priceMinor, String currency, Integer quantity) { }
