package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.dto.telegram.ProductSource;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LiveProductSources implements ProductSourceLookup {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LiveProductSources.class);
    private static final String WIKI = "https://en.wikipedia.org/w/api.php";
    private final HttpClient http;
    private final JsonMapper json;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public LiveProductSources(JsonMapper json, Clock clock) {
        this(json, clock, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    LiveProductSources(JsonMapper json, Clock clock, HttpClient http) { this.json = json; this.clock = clock; this.http = http; }

    @Override
    public List<ProductSource> find(String model, int productIndex) {
        if (model == null || model.isBlank() || model.length() > 255 || model.indexOf('\0') >= 0) return List.of();
        List<ProductSource> result = new ArrayList<>();
        URI manufacturer = manufacturerUrl(model);
        if (manufacturer != null) {
            String page = fetch(manufacturer);
            if (page != null) {
                ParsedPage parsed = parseHtml(page, manufacturer.getHost().equals("www.apple.com"), model);
                String text = parsed.text();
                if (key(text).contains(key(withoutCapacity(model)))) result.add(new ProductSource("p" + productIndex + "-manufacturer", productIndex,
                        model + " manufacturer specifications", manufacturer.toString(), excerpt(text, 4000), clock.instant(), parsed.specifications()));
            }
        }
        try {
            String search = fetch(URI.create(WIKI + "?action=query&list=search&srlimit=3&format=json&srsearch=" + encode(withoutCapacity(model))));
            if (search != null) {
                for (var hit : json.readTree(search).path("query").path("search")) {
                    String title = hit.path("title").asText();
                    boolean exact = key(title).equals(key(withoutCapacity(model)));
                    boolean family = key(withoutCapacity(model)).matches(Pattern.quote(key(title)) + "(?:ultra|plus|promax|max)");
                    if (!exact && !family) continue;
                    String page = fetch(URI.create(WIKI + "?action=parse&prop=text&formatversion=2&format=json&page=" + encode(title)));
                    if (page == null) continue;
                    String text = htmlText(json.readTree(page).path("parse").path("text").asText());
                    if (text.isBlank() || family && !key(text).contains(key(withoutCapacity(model)))) continue;
                    result.add(source(productIndex, "wikipedia", title + " (Wikipedia)", "https://en.wikipedia.org/wiki/" + encode(title.replace(' ', '_')), excerpt(text, 4000)));
                    break;
                }
            }
        } catch (RuntimeException ignored) { /* A failed public provider must not turn into invented specifications. */ }
        return List.copyOf(result);
    }
    private ProductSource source(int index, String provider, String title, String url, String text) {
        return new ProductSource("p" + index + "-" + provider, index, title, url, text, clock.instant());
    }
    private String fetch(URI uri) {
        // Never follow a provider-supplied URL or a user-supplied host. No credentials or chat text are sent.
        if (!"https".equals(uri.getScheme()) || !Set.of("www.apple.com", "www.samsung.com", "en.wikipedia.org").contains(uri.getHost())) return null;
        try {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "PriceChecker/1.0 (local product specification lookup)")
                    .header("Accept", "application/json,text/html").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200) { log.debug("Product source {} returned HTTP {}", uri.getHost(), response.statusCode()); return null; }
                byte[] bytes = body.readNBytes(4_194_305);
                if (bytes.length > 4_194_304) { log.debug("Product source {} exceeded the response size limit", uri.getHost()); return null; }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); return null; }
        catch (IOException | RuntimeException exception) { log.debug("Product source {} unavailable: {}", uri.getHost(), exception.getClass().getSimpleName()); return null; }
    }
    static URI manufacturerUrl(String model) {
        String name = withoutCapacity(model);
        var iphone = Pattern.compile("(?i)^(?:Apple\\s+)?iPhone\\s*(\\d{1,2})(?:\\s*(Pro(?:\\s*Max)?|Plus|mini|e))?$").matcher(name);
        if (iphone.matches()) {
            String suffix = iphone.group(2) == null ? "" : iphone.group(2).toLowerCase(Locale.ROOT).replace(" ", "");
            if (suffix.equals("promax")) suffix = "pro";
            return URI.create("https://www.apple.com/iphone-" + iphone.group(1) + (suffix.isEmpty() || suffix.equals("e") ? suffix : "-" + suffix) + "/specs/");
        }
        var galaxy = Pattern.compile("(?i)^(?:Samsung\\s+)?Galaxy\\s+(S\\d{1,3})(?:\\s+(Ultra|Plus))?$").matcher(name);
        if (galaxy.matches()) return URI.create("https://www.samsung.com/global/galaxy/galaxy-" + galaxy.group(1).toLowerCase(Locale.ROOT)
                + (galaxy.group(2) == null ? "" : "-" + galaxy.group(2).toLowerCase(Locale.ROOT)) + "/specs/");
        return null;
    }
    static String htmlText(String html) {
        return parseHtml(html, false, "").text();
    }
    record ParsedPage(String text, Map<String, String> specifications) { }
    static ParsedPage parseHtml(String html, boolean apple, String model) {
        // ParserDelegator's legacy HTML grammar can expose modern inline style contents as text.
        html = html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>", " ").replaceAll("(?s)<!--.*?-->", " ");
        var main = Pattern.compile("(?is)<main\\b[^>]*>(.*)</main>").matcher(html);
        if (main.find()) html = main.group(1);
        StringBuilder text = new StringBuilder();
        Map<String, String> specifications = new LinkedHashMap<>();
        boolean paired = apple && html.contains("header-iphone-pro-max");
        int selected = key(model).endsWith("promax") ? 1 : 0;
        try {
            new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
                private int hidden;
                private int depth, rowDepth = -1, headerDepth = -1, skipDepth = -1, column;
                private String sectionLabel;
                private final StringBuilder label = new StringBuilder(), value = new StringBuilder();
                public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) hidden++;
                    if (tag == HTML.Tag.DIV) {
                        depth++;
                        String classes = " " + Objects.toString(attributes.getAttribute(HTML.Attribute.CLASS), "") + " ";
                        if (classes.contains(" techspecs-section ")) sectionLabel = null;
                        if (apple && classes.contains(" techspecs-row ")) { rowDepth = depth; column = 0; label.setLength(0); value.setLength(0); }
                        if (rowDepth >= 0 && classes.contains(" techspecs-rowheader ")) headerDepth = depth;
                        if (rowDepth >= 0 && classes.contains(" techspecs-column ")) {
                            boolean shared = "2".equals(Objects.toString(attributes.getAttribute("aria-colspan"), ""));
                            if (paired && !shared && column++ != selected) skipDepth = depth;
                        }
                    }
                    if (tag == HTML.Tag.TR || tag == HTML.Tag.P || tag == HTML.Tag.H2 || tag == HTML.Tag.H3) text.append('\n');
                }
                public void handleEndTag(HTML.Tag tag, int pos) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) hidden = Math.max(0, hidden - 1);
                    if (tag == HTML.Tag.DIV) {
                        if (depth == headerDepth) headerDepth = -1;
                        if (depth == skipDepth) skipDepth = -1;
                        if (depth == rowDepth) {
                            String canonical = canonicalLabel(label.toString());
                            if (canonical != null) sectionLabel = canonical;
                            else if (label.toString().isBlank()) canonical = sectionLabel;
                            String content = normalizeText(value.toString());
                            if (canonical != null && !content.isBlank()) specifications.merge(canonical, content, (first, next) -> first + " " + next);
                            rowDepth = -1;
                        }
                        depth--;
                    }
                    if (tag == HTML.Tag.TR || tag == HTML.Tag.P || tag == HTML.Tag.H2 || tag == HTML.Tag.H3) text.append('\n');
                }
                public void handleText(char[] data, int pos) {
                    if (hidden != 0 || skipDepth >= 0) return;
                    text.append(data).append(' ');
                    if (rowDepth >= 0) { if (headerDepth >= 0) label.append(data).append(' '); else value.append(data).append(' '); }
                }
            }, true);
        } catch (IOException ignored) { return new ParsedPage("", Map.of()); }
        String dimensions = specifications.getOrDefault("Dimensions", "");
        var weight = Pattern.compile("(?i)Weight:\\s*([^\\n]+)").matcher(dimensions);
        if (weight.find()) specifications.put("Weight", weight.group(1).strip());
        String battery = specifications.getOrDefault("Battery", "");
        var charging = Pattern.compile("(?i)(?:Fast[\\p{Pd}-]charge capable:|MagSafe wireless charging)[^\\n]*").matcher(battery);
        if (charging.find()) specifications.put("Charging", charging.group().strip());
        specifications.replaceAll((label, value) -> value.length() > 1200 ? value.substring(0, 1197) + "..." : value);
        return new ParsedPage(normalizeText(text.toString()), Map.copyOf(specifications));
    }
    private static String normalizeText(String value) { return value.replace('\u00a0', ' ').replaceAll("[\\t\\x0B\\f\\r ]+", " ").replaceAll(" *\\n+ *", "\n").strip(); }
    private static String canonicalLabel(String label) {
        return switch (key(label).replaceFirst("\\d+$", "")) {
            case "capacity" -> "Storage";
            case "display" -> "Display";
            case "chip" -> "Chipset";
            case "camera", "procamerasystem", "dualcamerasystem" -> "Rear camera";
            case "truedepthcamera", "centerstagecamera", "frontcamera" -> "Front camera";
            case "powerandbattery" -> "Battery";
            case "operatingsystem" -> "OS";
            case "sizeandweight" -> "Dimensions";
            case "splashwateranddustresistant" -> "Water resistance";
            case "cellularandwireless" -> "Connectivity";
            default -> null;
        };
    }
    private static String excerpt(String text, int maximum) {
        if (text.length() <= maximum) return text;
        // Manufacturer specs and Wikipedia infoboxes lead with the useful product data.
        return text.substring(0, maximum);
    }
    private static String withoutCapacity(String model) {
        return model.strip().replaceFirst("\\s*\\[[^\\]]+\\]$", "").replaceFirst("(?i)\\s*\\[?(?:64|128|256|512)\\s*GB\\]?.*$", "")
                .replaceFirst("(?i)\\s*\\[?[12]\\s*TB\\]?.*$", "").strip();
    }
    private static String key(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("\\b(?:apple|samsung)\\b", "").replaceAll("[^\\p{L}\\p{N}]", ""); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
