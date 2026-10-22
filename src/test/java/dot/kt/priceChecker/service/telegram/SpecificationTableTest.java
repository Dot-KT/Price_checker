package dot.kt.priceChecker.service.telegram;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SpecificationTableTest {
    @Test void singleProductAndComparisonAlwaysHaveRealTableStructure() {
        var single = SpecificationTable.render(List.of("iPhone 17"), Map.of("Chipset", List.of("Apple A19")));
        assertThat(single.richHtml()).contains("<table", "<th>iPhone 17</th>", "<td>Apple A19</td>");
        var comparison = SpecificationTable.render(List.of("iPhone 17", "iPhone 18 Pro"), Map.of("Chipset", List.of("A19", "A20 Pro")));
        assertThat(comparison.richHtml()).contains("<th>iPhone 17</th><th>iPhone 18 Pro</th>", "<td>A19</td><td>A20 Pro</td>");
        assertThat(comparison.text()).contains("A: iPhone 17", "B: iPhone 18 Pro", "| Specification");
        assertThat(comparison.text().lines().filter(line -> line.startsWith("|")).map(String::length).distinct()).hasSize(1);
    }
    @Test void sourceOrModelTextCannotInjectHtmlAndMissingCellsStayUnverified() {
        var table = SpecificationTable.render(List.of("<script>bad</script>", "Device B"), Map.of("Display", List.of("4 < 5 & 6")));
        assertThat(table.richHtml()).contains("&lt;script&gt;bad&lt;/script&gt;", "4 &lt; 5 &amp; 6", "<td>Not verified</td>").doesNotContain("<script>");
    }
    @Test void wrapsLongCellsWithoutLosingTheirContentOrBreakingSurrogatePairs() {
        String value = "😀".repeat(35);
        var table = SpecificationTable.render(List.of("Device"), Map.of("Features", List.of(value)));
        assertThat(table.text().replace("|", "").replace(" ", "").replace("\n", "")).contains(value);
    }
}
