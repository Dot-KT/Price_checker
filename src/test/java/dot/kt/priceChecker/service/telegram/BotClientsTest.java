package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.config.telegram.*;
import dot.kt.priceChecker.dto.telegram.*;
import dot.kt.priceChecker.enums.catalog.*;
import java.io.IOException;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BotClientsTest {
    final JsonMapper json = JsonMapper.builder().build();
    final HttpClient http = mock(HttpClient.class);
    @Test void telegramParses64BitIdsAndCallbacksWithoutTreatingBotMessageAsUserText() throws Exception {
        respond(200, """
                {"ok":true,"result":[{"update_id":42,"callback_query":{"id":"cb","data":"choice","from":{"id":5000000001,"username":"ada","first_name":"Ada"},"message":{"text":"Old bot prompt","chat":{"id":5000000001,"type":"private"}}}}]}
                """);
        var api = telegram(); var updates = api.poll(40);
        assertThat(updates).singleElement().satisfies(update -> {
            assertThat(update.telegramId()).isEqualTo(5_000_000_001L); assertThat(update.chatId()).isEqualTo(5_000_000_001L);
            assertThat(update.callbackData()).isEqualTo("choice"); assertThat(update.text()).isNull(); assertThat(update.privateChat()).isTrue();
        });
        var request = ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(request.capture(), anyHandler());
        var body = json.readTree(body(request.getValue()));
        assertThat(body.path("offset").asLong()).isEqualTo(40); assertThat(body.path("allowed_updates").size()).isEqualTo(2);
    }
    @Test void telegramUsesPlainTextAndInlineButtonsAndHonorsRetryAfterWithoutLeakingToken() throws Exception {
        respond(200, "{\"ok\":true,\"result\":{\"message_id\":1}}");
        var api = telegram(); api.send(42, new BotReply("Product <text>", List.of(List.of(BotReply.Button.action("Choose", "c:abc:y")))));
        var request = ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(request.capture(), anyHandler());
        var payload = json.readTree(body(request.getValue()));
        assertThat(payload.has("parse_mode")).isFalse(); assertThat(payload.path("reply_markup").path("inline_keyboard").get(0).get(0).path("callback_data").asText()).isEqualTo("c:abc:y");
        respond(429, "{\"ok\":false,\"error_code\":429,\"parameters\":{\"retry_after\":41}}");
        assertThatThrownBy(() -> api.send(42, BotReply.text("hi"))).isInstanceOfSatisfying(TelegramApiClient.TelegramFailure.class,
                failure -> { assertThat(failure.retrySeconds()).isEqualTo(41); assertThat(failure.getMessage()).doesNotContain("test-secret"); });
    }
    @Test void ollamaUsesStructuredLocalOutputAndKeepsDatabaseIdsOutsideTheModelContract() throws Exception {
        respond(200, json.writeValueAsString(Map.of("message", Map.of("content", "{\"items\":[\"Samsung Galaxy S24 Ultra\"],\"price\":true,\"details\":false,\"comparison\":false,\"currency\":\"ngn\",\"wrongDomain\":false}"))));
        var assistant = new OllamaProductAssistant(new OllamaAssistantProperties(), json, http);
        var result = assistant.identify(work()); assertThat(result.items()).containsExactly("Samsung Galaxy S24 Ultra"); assertThat(result.currency()).isEqualTo("NGN");
        var request = ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(request.capture(), anyHandler());
        var payload = json.readTree(body(request.getValue()));
        assertThat(payload.path("format").path("type").asText()).isEqualTo("object");
        assertThat(payload.path("format").path("properties").path("wrongDomain").path("type").asText()).isEqualTo("boolean");
        assertThat(payload.path("format").path("required").toString()).contains("wrongDomain", "items");
        assertThat(payload.path("stream").asBoolean()).isFalse();
        assertThat(payload.path("think").asBoolean()).isFalse(); assertThat(payload.path("options").path("num_ctx").asInt()).isEqualTo(4096);
        assertThat(payload.toString()).contains("GADGET", "PHONE").doesNotContain(work().conversationId().toString());
    }
    @Test void invalidOllamaOutputAndNetworkFailureAreRecoverableAndCloudCanDisableJsonMode() throws Exception {
        var properties = new OllamaAssistantProperties(); properties.setStructuredOutput(false);
        var assistant = new OllamaProductAssistant(properties, json, http);
        respond(200, "{\"message\":{\"content\":\"not JSON\"}}");
        assertThatThrownBy(() -> assistant.identify(work())).isInstanceOf(IllegalStateException.class);
        var request = ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(request.capture(), anyHandler());
        assertThat(json.readTree(body(request.getValue())).has("format")).isFalse();
        when(http.send(any(HttpRequest.class), anyHandler())).thenThrow(new IOException("offline"));
        assertThatThrownBy(() -> assistant.identify(work())).hasMessage("Ollama is unavailable.");
    }
    @Test void longUnicodeRepliesSplitBelowTelegramLimitAndKeepButtonsOnlyOnLastChunk() {
        var replies = BotReply.split(List.of(new BotReply("😀".repeat(4000), List.of(List.of(BotReply.Button.action("yes", "y"))))));
        assertThat(replies).hasSize(2); assertThat(replies.getFirst().buttons()).isEmpty(); assertThat(replies.getLast().buttons()).hasSize(1);
        assertThat(replies.stream().map(BotReply::text).reduce("", String::concat)).isEqualTo("😀".repeat(4000));
        assertThat(replies).allMatch(reply -> reply.text().codePointCount(0, reply.text().length()) <= 3500);
    }
    @Test void nativeTablesUseSendRichMessageAndKeepTheSellerButtons() throws Exception {
        respond(200, "{\"ok\":true,\"result\":{\"message_id\":1}}");
        var report = SpecificationTable.render(List.of("Device"), Map.of("Chipset", List.of("A19")));
        telegram().send(42, new BotReply(report.text(), List.of(List.of(BotReply.Button.action("Show sellers", "c:abc:y"))), report.richHtml()));
        var request = ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(request.capture(), anyHandler());
        var payload = json.readTree(body(request.getValue()));
        assertThat(request.getValue().uri().getPath()).endsWith("/sendRichMessage");
        assertThat(payload.path("rich_message").path("html").asText()).contains("<table", "<td>A19</td>");
        assertThat(payload.has("text")).isFalse(); assertThat(payload.has("reply_markup")).isTrue();
    }
    @Test void olderTelegramApiFallsBackToMonospaceTablesWithoutDroppingTheReply() throws Exception {
        @SuppressWarnings("unchecked") HttpResponse<String> unsupported = mock(HttpResponse.class);
        when(unsupported.statusCode()).thenReturn(404); when(unsupported.body()).thenReturn("{\"ok\":false,\"error_code\":404}");
        @SuppressWarnings("unchecked") HttpResponse<String> success = mock(HttpResponse.class);
        when(success.statusCode()).thenReturn(200); when(success.body()).thenReturn("{\"ok\":true,\"result\":{}}");
        when(http.send(any(HttpRequest.class), anyHandler())).thenReturn(unsupported, success);
        var report = SpecificationTable.render(List.of("Device"), Map.of("Chipset", List.of("A19")));
        telegram().send(42, new BotReply(report.text(), List.of(), report.richHtml()));
        var requests = ArgumentCaptor.forClass(HttpRequest.class); verify(http, times(2)).send(requests.capture(), anyHandler());
        var fallback = json.readTree(body(requests.getAllValues().getLast()));
        assertThat(requests.getAllValues().getLast().uri().getPath()).endsWith("/sendMessage");
        assertThat(fallback.path("entities").get(0).path("type").asText()).isEqualTo("pre");
        assertThat(fallback.path("text").asText()).contains("A19");
    }
    private TelegramApiClient telegram() { var props = new TelegramProperties(); props.setBotToken("test-secret"); return new TelegramApiClient(props, json, http); }
    private AssistantWork work() { return new AssistantWork(1, UUID.fromString("00000000-0000-0000-0000-000000000001"), ItemDomain.GADGET, GadgetType.PHONE,
            "S24 Ultra price?", List.of(), null, null, List.of()); }
    @SuppressWarnings("unchecked") private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), anyHandler())).thenReturn(response);
    }
    private static HttpResponse.BodyHandler<String> anyHandler() { return any(); }
    private static String body(HttpRequest request) {
        var result = new CompletableFuture<String>(); var bytes = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part); }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.join();
    }
}
