package dot.kt.priceChecker.dto.catalog;

import dot.kt.priceChecker.entities.inventory.InventoryItem;
import java.time.Instant;
import java.util.UUID;

public record OfferResponse(UUID inventoryItemId, UUID sellerId, String sellerName, String condition,
        long priceMinor, String currency, Integer quantity, Instant updatedAt) {
    public static OfferResponse from(InventoryItem item) {
        return new OfferResponse(item.getId(), item.getSeller().getId(), item.getSeller().getDisplayName(),
                item.getCondition(), item.getPriceMinor(), item.getCurrency(), item.getQuantity(), item.getUpdatedAt());
    }
}
