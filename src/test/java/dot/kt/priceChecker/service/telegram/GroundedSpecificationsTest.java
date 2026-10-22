package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.config.telegram.OllamaAssistantProperties;
import dot.kt.priceChecker.dto.telegram.*;
import dot.kt.priceChecker.enums.catalog.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroundedSpecificationsTest {
    final JsonMapper json = JsonMapper.builder().build();
    final HttpClient http = mock(HttpClient.class);
    final ProductSourceLookup lookup = mock(ProductSourceLookup.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC);
    final OllamaProductAssistant assistant = new OllamaProductAssistant(new OllamaAssistantProperties(), json, http, lookup, clock);
    @Test void performsFreshLookupAndAcceptsOnlyValuesWithMatchingEvidence() throws Exception {
        when(lookup.find("iPhone 17", 0)).thenReturn(List.of(source(0, "Apple A19 chipset. Memory: 8GB.")));
        respond("""
                {"rows":[{"label":"Chipset","values":[{"value":"Apple A19","sourceId":"p0-test","quote":"Apple A19 chipset."}]},
                {"label":"RAM","values":[{"value":"64GB","sourceId":"p0-test","quote":"Memory: 8GB."}]}]}
                """);
        var answer = assistant.explain(work(), List.of("iPhone 17"));
        assertThat(answer.richHtml()).contains("<td>Apple A19</td>").doesNotContain("<td>64GB</td>");
        assertThat(answer.text()).contains("Sources checked", "2026-10-22", "Not verified");
        verify(lookup).find("iPhone 17", 0);
    }
    @Test void comparisonCannotUseTheOtherProductsSourceOrInventReleaseStatus() throws Exception {
        when(lookup.find("iPhone 17", 0)).thenReturn(List.of(source(0, "Chipset Apple A19.")));
        when(lookup.find("iPhone 18 Pro", 1)).thenReturn(List.of(source(1, "Chipset Apple A20 Pro.")));
        respond("""
                {"rows":[{"label":"Chipset","values":[{"value":"Apple A20 Pro","sourceId":"p1-test","quote":"Chipset Apple A20 Pro."},
                {"value":"Apple A20 Pro","sourceId":"p1-test","quote":"Chipset Apple A20 Pro."}]},
                {"label":"Release date","values":[{"value":"Unreleased","sourceId":"p0-test","quote":"Not released yet."},null]}]}
                """);
        var answer = assistant.explain(work(), List.of("iPhone 17", "iPhone 18 Pro"));
        assertThat(answer.richHtml()).contains("<td>Not verified</td><td>Apple A20 Pro</td>").doesNotContain("Unreleased");
    }
    @Test void noSourcesMeansNoModelKnowledgeFallbackAndStillProducesATable() {
        when(lookup.find(anyString(), anyInt())).thenReturn(List.of());
        var answer = assistant.explain(work(), List.of("New phone 2026"));
        assertThat(answer.richHtml()).contains("<table", "Not verified");
        assertThat(answer.text()).contains("does not mean the product is unreleased"); verifyNoInteractions(http);
    }
    @Test void malformedExtractionDoesNotPreventAnUnverifiedTableWithSourceLinks() throws Exception {
        when(lookup.find(anyString(), anyInt())).thenReturn(List.of(source(0, "Chipset Apple A19.")));
        respond("iPhone 17 has not been released yet.");
        var answer = assistant.explain(work(), List.of("iPhone 17"));
        assertThat(answer.richHtml()).contains("<table", "extraction failed", "https://example.com/specs0").doesNotContain("not been released yet");
    }
    private ProductSource source(int index, String text) { return new ProductSource("p" + index + "-test", index, "Manufacturer", "https://example.com/specs" + index, text, clock.instant()); }
    private AssistantWork work() { return new AssistantWork(1, UUID.randomUUID(), ItemDomain.GADGET, GadgetType.PHONE, "specifications", List.of(), null, null, List.of()); }
    @SuppressWarnings("unchecked") private void respond(String text) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json.writeValueAsString(Map.of("message", Map.of("content", text))));
        when(http.send(any(HttpRequest.class), handler())).thenReturn(response);
    }
    private static HttpResponse.BodyHandler<String> handler() { return any(); }
}
