package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.config.telegram.TelegramProperties;
import dot.kt.priceChecker.dto.telegram.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TelegramApiClient {
    private final TelegramProperties properties;
    private final JsonMapper json;
    private final HttpClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public TelegramApiClient(TelegramProperties properties, JsonMapper json) {
        this(properties, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }
    TelegramApiClient(TelegramProperties properties, JsonMapper json, HttpClient http) { this.properties = properties; this.json = json; this.http = http; }
    public record Identity(long id, String username) { }
    public Identity identity() { var value = call("getMe", Map.of(), 20); return new Identity(value.path("id").asLong(), value.path("username").asText()); }
    public boolean hasWebhook() { return !call("getWebhookInfo", Map.of(), 20).path("url").asText("").isBlank(); }
    public List<TelegramIncoming> poll(long offset) {
        var result = call("getUpdates", Map.of("offset", offset, "timeout", properties.getPollTimeoutSeconds(),
                "limit", 100, "allowed_updates", List.of("message", "callback_query")), properties.getPollTimeoutSeconds() + 15);
        List<TelegramIncoming> updates = new ArrayList<>();
        for (var node : result) {
            boolean callback = node.has("callback_query");
            var event = callback ? node.path("callback_query") : node.path("message");
            var message = callback ? event.path("message") : event;
            var from = event.path("from");
            var chat = message.path("chat");
            updates.add(new TelegramIncoming(node.path("update_id").asLong(), from.path("id").asLong(), chat.path("id").asLong(),
                    nullable(from, "username"), nullable(from, "first_name"), nullable(from, "last_name"), callback ? null : nullable(message, "text"),
                    callback ? nullable(event, "data") : null, callback ? nullable(event, "id") : null,
                    chat.path("type").asText().equals("private") && !from.path("is_bot").asBoolean()));
        }
        return updates;
    }
    public void acknowledge(String callbackId) {
        if (callbackId != null) {
            try { call("answerCallbackQuery", Map.of("callback_query_id", callbackId), 10); }
            catch (TelegramFailure ignored) { /* An expired callback must not prevent its saved reply. */ }
        }
    }
    public void send(long chatId, BotReply reply) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId); payload.put("text", reply.text());
        if (!reply.buttons().isEmpty()) {
            List<List<Map<String, String>>> keyboard = reply.buttons().stream().map(row -> row.stream().map(button -> {
                Map<String, String> data = new LinkedHashMap<>(); data.put("text", button.text());
                if (button.url() != null) data.put("url", button.url()); else data.put("callback_data", button.callbackData());
                return data;
            }).toList()).toList();
            payload.put("reply_markup", Map.of("inline_keyboard", keyboard));
        }
        if (reply.richHtml() != null) {
            payload.remove("text"); payload.put("rich_message", Map.of("html", reply.richHtml()));
            try { call("sendRichMessage", payload, 20); return; }
            catch (TelegramFailure failure) { if (failure.status() != 400 && failure.status() != 404) throw failure; }
            // Older API deployments can still receive the same data as aligned monospace tables.
            for (BotReply fallback : BotReply.split(List.of(new BotReply(reply.text(), reply.buttons())))) send(chatId, fallback);
            return;
        }
        var tables = java.util.regex.Pattern.compile("(?m)^(?:\\+[-+]+\\+|\\|[^\\n]*\\|)(?:\\n(?:\\+[-+]+\\+|\\|[^\\n]*\\|))+").matcher(reply.text());
        List<Map<String, Object>> entities = new ArrayList<>();
        while (tables.find()) entities.add(Map.of("type", "pre", "offset", tables.start(), "length", tables.end() - tables.start()));
        if (!entities.isEmpty()) payload.put("entities", entities);
        call("sendMessage", payload, 20);
    }
    private JsonNode call(String method, Object payload, int timeout) {
        if (properties.getBotToken().isBlank()) throw new TelegramFailure(0, 60);
        try {
            var request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + properties.getBotToken().strip() + "/" + method))
                    .timeout(Duration.ofSeconds(timeout)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var body = json.readTree(response.body());
            if (response.statusCode() != 200 || !body.path("ok").asBoolean()) {
                throw new TelegramFailure(body.path("error_code").asInt(response.statusCode()),
                        Math.max(5, Math.min(3600, body.path("parameters").path("retry_after").asInt(30))));
            }
            return body.path("result");
        } catch (TelegramFailure exception) { throw exception; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new TelegramFailure(0, 30); }
        catch (Exception exception) { throw new TelegramFailure(0, 30); }
    }
    private static String nullable(JsonNode node, String field) { return node.hasNonNull(field) ? node.path(field).asText() : null; }
    public static class TelegramFailure extends RuntimeException {
        private final int status;
        private final int retrySeconds;
        public TelegramFailure(int status, int retrySeconds) { super("Telegram request failed (status " + status + ")."); this.status = status; this.retrySeconds = retrySeconds; }
        public int status() { return status; }
        public int retrySeconds() { return retrySeconds; }
    }
}
