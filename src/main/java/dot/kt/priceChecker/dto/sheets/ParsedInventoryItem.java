package dot.kt.priceChecker.dto.sheets;

public record ParsedInventoryItem(String sellerSku, String name, String model, String description, String condition,
                                  long priceMinor, String currency, Integer quantity, int sourceRow) {
}
