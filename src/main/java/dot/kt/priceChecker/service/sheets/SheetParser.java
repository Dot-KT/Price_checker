package dot.kt.priceChecker.service.sheets;

import dot.kt.priceChecker.config.sheets.SheetsProperties;
import dot.kt.priceChecker.dto.sheets.ParsedInventoryItem;
import dot.kt.priceChecker.dto.sheets.SheetParseResult;
import dot.kt.priceChecker.dto.sheets.SheetRowError;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SheetParser {
    private static final Pattern MONEY = Pattern.compile("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?");
    private static final Set<String> SUPPORTED_HEADERS = Set.of("sku", "name", "model", "description", "condition", "price", "currency", "quantity");
    private final SheetsProperties properties;

    public SheetParser(SheetsProperties properties) {
        this.properties = properties;
    }

    public SheetParseResult parse(List<List<Object>> rows, String defaultCurrency) {
        List<ParsedInventoryItem> items = new ArrayList<>();
        List<SheetRowError> errors = new ArrayList<>();
        int totalRows = rows.isEmpty() ? 0 : (int) rows.subList(1, rows.size()).stream().filter(row -> !blank(row)).count();
        if (rows.isEmpty()) {
            errors.add(error(1, "headers", "The sheet must contain a header row with name and price columns."));
            return new SheetParseResult(items, errors, totalRows);
        }
        if (rows.size() - 1 > properties.getMaxRows()) {
            errors.add(error(1, "sheet", "The sheet exceeds the maximum of " + properties.getMaxRows() + " data rows."));
            return new SheetParseResult(items, errors, totalRows);
        }
        Map<String, Integer> columns = headers(rows.getFirst(), errors);
        if (!errors.isEmpty()) return new SheetParseResult(items, errors, totalRows);
        if (totalRows == 0) {
            errors.add(error(1, "sheet", "The sheet must contain at least one product row. Empty imports cannot replace inventory."));
            return new SheetParseResult(items, errors, totalRows);
        }
        Map<String, Integer> seenSkus = new HashMap<>();
        for (int index = 1; index < rows.size(); index++) {
            List<Object> row = rows.get(index);
            if (blank(row)) continue;
            int sourceRow = index + 1;
            int before = errors.size();
            if (row.size() > properties.getMaxColumns()) {
                errors.add(error(sourceRow, "sheet", "The row exceeds the maximum of " + properties.getMaxColumns() + " columns."));
                continue;
            }
            String name = text(row, columns, "name");
            String model = nullable(text(row, columns, "model"));
            String description = nullable(text(row, columns, "description"));
            String condition = nullable(text(row, columns, "condition"));
            if (name.isEmpty()) errors.add(error(sourceRow, "name", "Name is required."));
            length(name, 255, sourceRow, "name", errors);
            length(model, 255, sourceRow, "model", errors);
            length(description, 4_000, sourceRow, "description", errors);
            length(condition, 255, sourceRow, "condition", errors);
            String sku = text(row, columns, "sku");
            if (sku.isEmpty() && !name.isEmpty()) sku = derivedSku(name, model);
            length(sku, 128, sourceRow, "sku", errors);
            if (!sku.isEmpty()) {
                Integer firstRow = seenSkus.putIfAbsent(sku, sourceRow);
                if (firstRow != null) errors.add(error(sourceRow, "sku", "Duplicate item key; it is already used on row " + firstRow + ". Use unique sku values."));
            }
            String currencyCode = text(row, columns, "currency");
            if (currencyCode.isEmpty()) currencyCode = defaultCurrency == null ? "" : defaultCurrency.trim();
            currencyCode = currencyCode.toUpperCase(Locale.ROOT);
            Currency currency = currency(currencyCode, sourceRow, errors);
            Long price = price(text(row, columns, "price"), currency, sourceRow, errors);
            Integer quantity = quantity(text(row, columns, "quantity"), sourceRow, errors);
            if (errors.size() == before) {
                items.add(new ParsedInventoryItem(sku, name, model, description, condition, price, currencyCode, quantity, sourceRow));
            }
        }
        return new SheetParseResult(items, errors, totalRows);
    }

    private Map<String, Integer> headers(List<Object> header, List<SheetRowError> errors) {
        Map<String, Integer> columns = new HashMap<>();
        Set<String> seen = new HashSet<>();
        if (header.size() > properties.getMaxColumns()) {
            errors.add(error(1, "headers", "The sheet exceeds the maximum of " + properties.getMaxColumns() + " columns."));
        }
        for (int index = 0; index < header.size(); index++) {
            String name = value(header.get(index)).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
            name = switch (name) { case "seller_sku", "no.", "no" -> "sku"; default -> name; };
            if (name.isEmpty()) continue;
            if (!seen.add(name)) errors.add(error(1, name, "Duplicate column header: " + name + "."));
            if (SUPPORTED_HEADERS.contains(name)) columns.put(name, index);
        }
        for (String required : List.of("name", "price")) {
            if (!columns.containsKey(required)) errors.add(error(1, required, "Missing required column: " + required + "."));
        }
        return columns;
    }

    private static Currency currency(String code, int row, List<SheetRowError> errors) {
        try {
            if (!code.matches("[A-Z]{3}")) throw new IllegalArgumentException();
            Currency currency = Currency.getInstance(code);
            if (currency.getDefaultFractionDigits() < 0 || code.equals("XXX")) throw new IllegalArgumentException();
            return currency;
        } catch (IllegalArgumentException exception) {
            errors.add(error(row, "currency", "Use a valid ISO 4217 currency code, such as NGN or USD."));
            return null;
        }
    }

    private static Long price(String raw, Currency currency, int row, List<SheetRowError> errors) {
        if (raw.isEmpty()) {
            errors.add(error(row, "price", "Price is required."));
            return null;
        }
        if (raw.length() > 64 || !MONEY.matcher(raw).matches()) {
            errors.add(error(row, "price", "Use a nonnegative decimal price, such as 1234.50 or 1,234.50."));
            return null;
        }
        if (currency == null) return null;
        try {
            int digits = currency.getDefaultFractionDigits();
            return new BigDecimal(raw.replace(",", "")).setScale(digits, RoundingMode.UNNECESSARY)
                    .movePointRight(digits).longValueExact();
        } catch (ArithmeticException exception) {
            errors.add(error(row, "price", "Price has too many decimal places for this currency or exceeds the supported range."));
            return null;
        }
    }

    private static Integer quantity(String raw, int row, List<SheetRowError> errors) {
        if (raw.isEmpty()) return null;
        try {
            if (raw.length() > 32 || !raw.matches("[0-9]+(?:\\.0+)?")) throw new ArithmeticException();
            return new BigDecimal(raw).intValueExact();
        } catch (ArithmeticException exception) {
            errors.add(error(row, "quantity", "Quantity must be a whole number between 0 and 2147483647."));
            return null;
        }
    }

    private static String derivedSku(String name, String model) {
        String identity = name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT) + "\u0000"
                + (model == null ? "" : model.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return "auto:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String text(List<Object> row, Map<String, Integer> columns, String header) {
        Integer index = columns.get(header);
        return index == null || index >= row.size() ? "" : value(row.get(index));
    }

    private static String value(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        if (value instanceof Number number) {
            try { return new BigDecimal(number.toString()).toPlainString(); }
            catch (NumberFormatException ignored) { return number.toString(); }
        }
        return value.toString().strip();
    }

    private static boolean blank(List<Object> row) { return row.stream().allMatch(cell -> value(cell).isBlank()); }
    private static String nullable(String value) { return value.isEmpty() ? null : value; }
    private static void length(String value, int max, int row, String field, List<SheetRowError> errors) {
        if (value != null && value.length() > max) errors.add(error(row, field, "Must contain at most " + max + " characters."));
        if (value != null && value.indexOf('\0') >= 0) errors.add(error(row, field, "Must not contain a NUL character."));
    }
    private static SheetRowError error(int row, String field, String message) { return new SheetRowError(row, field, message); }
}
