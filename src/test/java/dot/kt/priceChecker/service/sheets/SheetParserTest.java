package dot.kt.priceChecker.service.sheets;

import dot.kt.priceChecker.config.sheets.SheetsProperties;
import dot.kt.priceChecker.dto.sheets.ParsedInventoryItem;
import dot.kt.priceChecker.dto.sheets.SheetRowError;
import dot.kt.priceChecker.enums.catalog.ItemDomain;
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
    void gadgetSheetsRequireTypeAndNonPhoneGadgetsDoNotRequireSeries() {
        var result = parser.parse(List.of(row("name", "price", "category", "brand", "gadget_type", "series"),
                row("Laptop", "100", "GADGET", "Lenovo", "LAPTOP", ""),
                row("Phone", "200", "GADGET", "Samsung", "PHONE", "S")), "NGN");
        assertThat(result.errors()).isEmpty();
        assertThat(result.items().getFirst().catalog().gadgetType()).isEqualTo(dot.kt.priceChecker.enums.catalog.GadgetType.LAPTOP);
        assertThat(result.items().getFirst().catalog().series()).isNull();
        var missing = parser.parse(List.of(row("name", "price", "category", "brand"), row("Laptop", "100", "GADGET", "Lenovo")), "NGN");
        assertThat(missing.errors()).anyMatch(error -> error.field().equals("gadget_type"));
    }

    @Test
    void parsesHeadersAmountsAndOptionalColumns() {
        var result = parser.parse(List.of(
                row("\uFEFF Name ", "PRICE", "Seller SKU", "model", "description", "quantity", "currency"),
                row("iPhone 15", "1,234.50", "IPH-15", "128 GB", "Blue", new BigDecimal("2.0"), "ngn")), "USD");
        assertThat(result.errors()).isEmpty();
        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.items()).containsExactly(new ParsedInventoryItem("IPH-15", "iPhone 15", "128 GB", "Blue", null, 123450, "NGN", 2, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Brand new", "Used", "Mint", "Has issues: cracked screen", "Refurbished", "Open box", ""})
    void acceptsFreeTextConditionAndTreatsBlankAsUnknown(String condition) {
        var result = parser.parse(List.of(row("name", "price", " Condition "),
                row("Phone", "100", "  " + condition + "  ")), "NGN");
        assertThat(result.errors()).isEmpty();
        assertThat(result.items().getFirst().condition()).isEqualTo(condition.isEmpty() ? null : condition);
    }

    @Test
    void validatesConditionLengthAndRejectsNulBeforeDatabaseWrites() {
        var valid = parser.parse(List.of(row("name", "price", "condition"),
                row("Phone", "100", "x".repeat(255))), "NGN");
        assertThat(valid.errors()).isEmpty();
        for (String condition : List.of("x".repeat(256), "Cracked\0screen")) {
            var invalid = parser.parse(List.of(row("name", "price", "condition"),
                    row("Phone", "100", condition)), "NGN");
            assertThat(invalid.items()).isEmpty();
            assertThat(invalid.errors()).singleElement().satisfies(error -> {
                assertThat(error.field()).isEqualTo("condition");
                assertThat(error.rowNumber()).isEqualTo(2);
            });
        }
    }

    @Test
    void parsesExplicitPhoneAndApplianceClassificationAndHeaderAliases() {
        var result = parser.parse(List.of(
                row("sku", "name", "price", "category", "company", "series", "type", "size", "features"),
                row("PHONE", "Galaxy S24", "100", "Phones", " Samsung ", " S ", "", "", ""),
                row("TV", "LG TV", "200", "Electrical appliances", "Life is good", "", "Television", "55 inch", "4K;Smart TV")), "NGN");
        assertThat(result.errors()).isEmpty();
        var phone = result.items().getFirst().catalog();
        assertThat(phone.category()).isEqualTo(ItemDomain.GADGET);
        assertThat(phone.brand()).isEqualTo("Samsung");
        assertThat(phone.series()).isEqualTo("S");
        var appliance = result.items().get(1).catalog();
        assertThat(appliance.category()).isEqualTo(ItemDomain.ELECTRICAL_APPLIANCE);
        assertThat(appliance.brand()).isEqualTo("LG");
        assertThat(appliance.applianceType()).isEqualTo("Television");
        assertThat(appliance.size()).isEqualTo("55 inch");
    }

    @Test
    void legacyRowsWithoutCategoryStayUnclassifiedAndDoNotGuessTheirDomain() {
        var result = parser.parse(List.of(row("name", "price", "brand", "series"),
                row("Samsung phone", "100", "Samsung", "S")), "NGN");
        assertThat(result.errors()).isEmpty();
        assertThat(result.items().getFirst().catalog()).isNull();
    }

    @Test
    void rejectsInvalidCategoryCrossDomainFieldsAndUnsafeCatalogText() {
        var result = parser.parse(List.of(
                row("sku", "name", "price", "category", "brand", "series", "appliance_type", "size", "features"),
                row("A", "Phone", "100", "PHONE", "Samsung", "S", "Television", "", ""),
                row("B", "Phone", "100", "PHONE", "Samsung", "", "", "", ""),
                row("C", "TV", "100", "ELECTRICAL_APPLIANCE", "Hisense", "", "TV", "55 inch", "x".repeat(1001)),
                row("D", "Phone", "100", "PHONE", "Sam\0sung", "S", "", "", ""),
                row("E", "Toy", "100", "TOY", "Brand", "", "", "", "")), "NGN");
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).extracting(SheetRowError::field)
                .contains("category", "series", "features", "brand");
        assertThat(result.errors()).anyMatch(error -> error.rowNumber() == 4 && error.field().equals("features"));
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
