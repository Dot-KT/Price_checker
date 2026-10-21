package dot.kt.priceChecker.dto.inventory;

import java.util.List;

public record InventoryPage(List<InventoryItemResponse> items, int page, int size,
        long totalElements, int totalPages) {
}
