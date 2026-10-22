package dot.kt.priceChecker.dto.sheets;

import dot.kt.priceChecker.dto.catalog.CatalogAttributes;

public record ParsedInventoryItem(String sellerSku, String name, String model, String description, String condition,
                                  long priceMinor, String currency, Integer quantity, int sourceRow, CatalogAttributes catalog) {
    public ParsedInventoryItem(String sellerSku, String name, String model, String description, String condition,
            long priceMinor, String currency, Integer quantity, int sourceRow) {
        this(sellerSku, name, model, description, condition, priceMinor, currency, quantity, sourceRow, null);
    }
}
