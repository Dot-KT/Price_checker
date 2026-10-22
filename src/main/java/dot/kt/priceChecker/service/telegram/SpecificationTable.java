package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.dto.telegram.SpecificationReport;
import java.util.*;

/** The application, rather than the model, owns table structure and HTML escaping. */
public final class SpecificationTable {
    private SpecificationTable() { }
    public static SpecificationReport render(List<String> names, Map<String, List<String>> rows) {
        return render(names, rows, 180);
    }
    public static SpecificationReport render(List<String> names, Map<String, List<String>> rows, int cellLimit) {
        if (names.isEmpty() || names.size() > 2) throw new IllegalArgumentException("A table needs one or two products.");
        StringBuilder text = new StringBuilder();
        if (names.size() == 1) text.append("Product: ").append(clean(names.getFirst())).append('\n');
        else { text.append("A: ").append(clean(names.get(0))).append("\nB: ").append(clean(names.get(1))).append('\n'); }
        int[] widths = names.size() == 1 ? new int[] {18, 36} : new int[] {16, 23, 23};
        List<String> headers = new ArrayList<>(List.of("Specification"));
        headers.add(names.size() == 1 ? "Value" : "A"); if (names.size() == 2) headers.add("B");
        String border = border(widths);
        text.append(border).append('\n'); plainRow(text, headers, widths); text.append(border).append('\n');
        StringBuilder html = new StringBuilder("<table bordered striped compact><tr><th>Specification</th>");
        names.forEach(name -> html.append("<th>").append(escape(clean(name))).append("</th>")); html.append("</tr>");
        rows.entrySet().stream().limit(24).forEach(row -> {
            List<String> values = new ArrayList<>(List.of(clean(row.getKey())));
            for (int index = 0; index < names.size(); index++) values.add(clean(index < row.getValue().size() ? row.getValue().get(index) : "Not verified", cellLimit));
            plainRow(text, values, widths); text.append(border).append('\n');
            html.append("<tr><th>").append(escape(values.getFirst())).append("</th>");
            values.stream().skip(1).forEach(value -> html.append("<td>").append(escape(value)).append("</td>")); html.append("</tr>");
        });
        return new SpecificationReport(text.toString().stripTrailing(), html.append("</table>").toString());
    }
    public static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private static String clean(String value) {
        return clean(value, 180);
    }
    private static String clean(String value, int limit) {
        if (value == null || value.isBlank()) return "Not verified";
        String result = value.replaceAll("[\\p{Cc}|]+", " ").strip();
        return result.codePointCount(0, result.length()) > limit ? result.substring(0, result.offsetByCodePoints(0, limit - 3)) + "..." : result;
    }
    private static String border(int[] widths) { StringBuilder value = new StringBuilder("+"); for (int width : widths) value.append("-".repeat(width + 2)).append('+'); return value.toString(); }
    private static void plainRow(StringBuilder output, List<String> values, int[] widths) {
        List<List<String>> wrapped = new ArrayList<>(); int height = 1;
        for (int index = 0; index < values.size(); index++) { var lines = wrap(values.get(index), widths[index]); wrapped.add(lines); height = Math.max(height, lines.size()); }
        for (int line = 0; line < height; line++) {
            output.append('|');
            for (int index = 0; index < values.size(); index++) {
                String part = line < wrapped.get(index).size() ? wrapped.get(index).get(line) : "";
                output.append(' ').append(part).append(" ".repeat(widths[index] - part.codePointCount(0, part.length()) + 1)).append('|');
            }
            output.append('\n');
        }
    }
    private static List<String> wrap(String value, int width) {
        List<String> result = new ArrayList<>();
        while (value.codePointCount(0, value.length()) > width) {
            int end = value.offsetByCodePoints(0, width); int space = value.lastIndexOf(' ', end);
            if (space > end / 2) end = space;
            result.add(value.substring(0, end)); value = value.substring(end).stripLeading();
        }
        result.add(value); return result;
    }
}
