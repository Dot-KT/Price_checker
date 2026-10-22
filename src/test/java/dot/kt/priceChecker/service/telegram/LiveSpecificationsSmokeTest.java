package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.config.telegram.OllamaAssistantProperties;
import dot.kt.priceChecker.dto.telegram.AssistantWork;
import dot.kt.priceChecker.enums.catalog.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in: public product pages and the local Ollama server, never Telegram messages. */
@EnabledIfSystemProperty(named = "liveSpecificationsSmoke", matches = "true")
class LiveSpecificationsSmokeTest {
    @Test void publicApiFallbackCanSupplyEvidenceWithoutAManufacturerParser() throws Exception {
        var json = JsonMapper.builder().build(); var clock = Clock.systemUTC();
        var properties = new OllamaAssistantProperties();
        properties.setModel(System.getProperty("liveOllamaModel", properties.getModel())); properties.setTimeoutSeconds(180);
        var lookup = new LiveProductSources(json, clock);
        var assistant = new OllamaProductAssistant(properties, json,
                (name, index) -> lookup.find(name, index).stream().filter(source -> source.id().endsWith("wikipedia")).toList(), clock);
        var work = new AssistantWork(0, UUID.randomUUID(), ItemDomain.GADGET, GadgetType.PHONE,
                "Specifications of iPhone 17", List.of(), null, null, List.of());
        var answer = assistant.explain(work, List.of("iPhone 17"));
        assertThat(answer.richHtml()).contains("<table", "Apple A19", "8 GB", "wikipedia.org");
        assertThat(answer.text()).doesNotContain("extraction failed", "has not been released yet");
    }
    @Test void recognizesANewerProductAndProducesASingleProductTable() throws Exception {
        var json = JsonMapper.builder().build(); var clock = Clock.systemUTC();
        var properties = new OllamaAssistantProperties();
        properties.setModel(System.getProperty("liveOllamaModel", properties.getModel())); properties.setTimeoutSeconds(180);
        var assistant = new OllamaProductAssistant(properties, json, new LiveProductSources(json, clock), clock);
        var work = new AssistantWork(0, UUID.randomUUID(), ItemDomain.GADGET, GadgetType.PHONE,
                "What are the specifications and price of iPhone 18 Pro?", List.of(), null, null, List.of());
        var intent = assistant.identify(work);
        assertThat(intent.items()).anyMatch(name -> name.toLowerCase(Locale.ROOT).contains("iphone 18 pro"));
        assertThat(intent.price()).isTrue();
        var answer = assistant.explain(work, List.of("iPhone 18 Pro"));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "live-single-specifications-smoke.html"), "<!doctype html><meta charset=\"utf-8\">" + answer.richHtml());
        assertThat(answer.richHtml()).contains("<table", "<th>iPhone 18 Pro</th>", "A20 Pro", "Front camera", "Sources checked");
        assertThat(answer.text()).doesNotContain("has not been released yet", "6.9");
    }
    @Test void comparesCurrentPublicProductSourcesUsingTheInstalledLocalModel() throws Exception {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LiveProductSources.class)).setLevel(ch.qos.logback.classic.Level.DEBUG);
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OllamaProductAssistant.class)).setLevel(ch.qos.logback.classic.Level.DEBUG);
        var json = JsonMapper.builder().build(); var clock = Clock.systemUTC();
        var properties = new OllamaAssistantProperties();
        properties.setModel(System.getProperty("liveOllamaModel", properties.getModel())); properties.setTimeoutSeconds(180);
        var sources = new LiveProductSources(json, clock);
        var assistant = new OllamaProductAssistant(properties, json, sources, clock);
        var work = new AssistantWork(0, UUID.randomUUID(), ItemDomain.GADGET, GadgetType.PHONE,
                "Compare the specifications of iPhone 17 and iPhone 18 Pro", List.of(), null, null, List.of());
        var answer = assistant.explain(work, List.of("iPhone 17", "iPhone 18 Pro"));
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "live-specifications-smoke.txt"), answer.text());
        Files.writeString(Path.of("target", "live-specifications-smoke.html"), "<!doctype html><meta charset=\"utf-8\">" + answer.richHtml());
        assertThat(answer.richHtml()).contains("<table", "iPhone 17", "iPhone 18 Pro", "Sources checked");
        assertThat(answer.text()).contains("A19", "A20 Pro").doesNotContain("has not been released yet", "6.9");
    }
}
