package dot.kt.priceChecker.service.sheets;

import dot.kt.priceChecker.config.sheets.SheetsProperties;
import dot.kt.priceChecker.dto.sheets.ParsedInventoryItem;
import dot.kt.priceChecker.dto.sheets.SheetRowError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SheetParserTest {
    private final SheetsProperties properties = new SheetsProperties();
    private final SheetParser parser = new SheetParser(properties);

    @Test
    void parsesHeadersAmountsAndOptionalColumns() {
        var result = parser.parse(List.of(
                row("\uFEFF Name ", "PRICE", "Seller SKU", "model", "description", "quantity", "currency"),
                row("iPhone 15", "1,234.50", "IPH-15", "128 GB", "Blue", new BigDecimal("2.0"), "ngn")), "USD");
        assertThat(result.errors()).isEmpty();
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.items()).containsExactly(new ParsedInventoryItem("IPH-15", "iPhone 15", "128 GB", "Blue", 123450, "NGN", 2, 2));
    }

    @Test
    void usesCurrencySpecificPrecisionAndDoesNotUseBinaryFloatingPointForMoney() {
        var result = parser.parse(List.of(row("name", "price", "currency"), row("Dinar item", new BigDecimal("12.345"), "KWD"), row("Yen item", 300.0, "JPY")), "NGN");
        assertThat(result.errors()).isEmpty();
        assertThat(result.items()).extracting(ParsedInventoryItem::priceMinor).containsExactly(12345L, 300L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.001", "-1", "NaN", "Infinity", "12,34.50", "1,234,56", "NGN 100", "1e2", "92233720368547758.08", ""})
    void rejectsInvalidOrUnrepresentablePricesWithoutRounding(String price) {
        var result = parser.parse(List.of(row("name", "price"), row("Item", price)), "NGN");
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).anyMatch(error -> error.rowNumber() == 2 && error.field().equals("price"));
    }

    @Test
    void derivesStableKeysFromNameAndModelAndReportsDuplicateKeys() {
        var original = parser.parse(List.of(row("name", "price", "model"), row("Phone", "100", "A1")), "NGN");
        var reordered = parser.parse(List.of(row("price", "model", "name"), row("200", "a1", " PHONE ")), "NGN");
        assertThat(original.items().getFirst().sellerSku()).startsWith("auto:").isEqualTo(reordered.items().getFirst().sellerSku());
        var duplicates = parser.parse(List.of(row("name", "price", "model"), row("Phone", "100", "A1"), row("phone", "200", "a1")), "NGN");
        assertThat(duplicates.errors()).anyMatch(error -> error.rowNumber() == 3 && error.field().equals("sku") && error.message().contains("row 2"));
    }

    @Test
    void acceptsNumberColumnAsSellerSkuAndDetectsExplicitDuplicates() {
        var result = parser.parse(List.of(row("No.", "name", "price"), row(1, "Phone", "100"), row(1, "Laptop", "200")), "NGN");
        assertThat(result.items().getFirst().sellerSku()).isEqualTo("1");
        assertThat(result.errors()).anyMatch(error -> error.field().equals("sku") && error.rowNumber() == 3);
    }

    @Test
    void skipsOnlyCompletelyEmptyRowsAndKeepsPhysicalRowNumbers() {
        var result = parser.parse(List.of(row("name", "price", "notes"), row(null, " ", ""), row("Item", "0"), row("", "", "Seller note")), "NGN");
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.items().getFirst().sourceRow()).isEqualTo(3);
        assertThat(result.items().getFirst().priceMinor()).isZero();
        assertThat(result.errors()).anyMatch(error -> error.rowNumber() == 4 && error.field().equals("name"));
    }

    @Test
    void reportsAllErrorsForEachInvalidRow() {
        var result = parser.parse(List.of(row("name", "price", "quantity", "currency", "sku", "model", "description"),
                row("x".repeat(256), "1.2", "-1", "XXX", "s".repeat(129), "m".repeat(256), "d".repeat(4001)),
                row("", "bad", "1.5", "not-currency")), "NGN");
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).extracting(SheetRowError::field).contains("name", "price", "quantity", "currency", "sku", "model", "description");
        assertThat(result.errors()).anyMatch(error -> error.rowNumber() == 3 && error.field().equals("quantity"));
    }

    @Test
    void rejectsMissingAndDuplicateHeadersIncludingAliases() {
        var missing = parser.parse(List.of(row("product", "amount"), row("Phone", "100")), "NGN");
        assertThat(missing.errors()).extracting(SheetRowError::field).containsExactly("name", "price");
        var duplicates = parser.parse(List.of(row("name", "price", "sku", "No.", "notes", "NOTES")), "NGN");
        assertThat(duplicates.errors()).extracting(SheetRowError::field).containsExactly("sku", "notes");
        assertThat(parser.parse(List.of(), "NGN").hasErrors()).isTrue();
    }

    @Test
    void rejectsRowsOrColumnsOverTheConfiguredLimit() {
        properties.setMaxRows(1);
        var rows = parser.parse(List.of(row("name", "price"), row("A", "1"), row("B", "2")), "NGN");
        assertThat(rows.items()).isEmpty();
        assertThat(rows.errors()).anyMatch(error -> error.field().equals("sheet"));
        properties.setMaxColumns(2);
        var columns = parser.parse(List.of(row("name", "price"), row("A", "1", "extra")), "NGN");
        assertThat(columns.items()).isEmpty();
        assertThat(columns.errors()).anyMatch(error -> error.rowNumber() == 2 && error.field().equals("sheet"));
    }

    @Test
    void rejectsHeadersOnlyAndWhitespaceOnlyImportsBeforeTheyCanReplaceInventory() {
        for (var rows : List.of(List.of(row("name", "price")), List.of(row("name", "price"), row(" ", "")))) {
            var result = parser.parse(rows, "NGN");
            assertThat(result.totalRows()).isZero();
            assertThat(result.items()).isEmpty();
            assertThat(result.errors()).anyMatch(error -> error.field().equals("sheet") && error.message().contains("at least one product"));
        }
    }

    @Test
    void rejectsNulInPersistedTextBeforeDatabaseWrites() {
        var result = parser.parse(List.of(row("sku", "name", "price", "model", "description"),
                row("S\0K", "Na\0me", "10", "Mo\0del", "Desc\0ription")), "NGN");
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).extracting(SheetRowError::field).containsExactlyInAnyOrder("sku", "name", "model", "description");
    }

    private static List<Object> row(Object... values) { return Arrays.asList(values); }
}
