package dot.kt.priceChecker.dto.inventory;

import dot.kt.priceChecker.entities.inventory.InventoryItem;
import dot.kt.priceChecker.dto.catalog.ItemResponse;
import java.time.Instant;
import java.util.UUID;

public record InventoryItemResponse(UUID id, String sellerSku, String name, String model,
        String description, String condition, long priceMinor, String currency, Integer quantity, Instant updatedAt,
        ItemResponse item) {
    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(item.getId(), item.getSellerSku(), item.getName(), item.getModel(),
                item.getDescription(), item.getCondition(), item.getPriceMinor(), item.getCurrency(), item.getQuantity(), item.getUpdatedAt(),
                item.getItem() == null ? null : ItemResponse.from(item.getItem()));
    }
}
