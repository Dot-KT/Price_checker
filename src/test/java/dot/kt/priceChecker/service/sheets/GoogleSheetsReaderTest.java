package dot.kt.priceChecker.service.sheets;

import dot.kt.priceChecker.config.sheets.SheetsProperties;
import dot.kt.priceChecker.exceptions.sheets.SheetAccessException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GoogleSheetsReaderTest {
    private static final String URL = "https://docs.google.com/spreadsheets/d/sheet-123/edit";
    private final SheetsProperties properties = new SheetsProperties();
    private final HttpClient client = mock(HttpClient.class);
    private final GoogleSheetsReader reader = new GoogleSheetsReader(properties, client);

    @Test
    void downloadsSelectedTabWithoutGoogleAuthenticationOrCopyingUntrustedQueryParameters() {
        respond(response(200, "sku,name,price\r\n000123,Phone,1234.50\r\n"));
        var result = reader.read(URL + "?usp=sharing&other=ignored#gid=42");
        assertThat(result.sourceUrl()).isEqualTo(URL + "#gid=42");
        assertThat(result.rows()).containsExactly(List.of("sku", "name", "price"), List.of("000123", "Phone", "1234.50"));
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(request.capture(), anyBodyHandler());
        assertThat(request.getValue().method()).isEqualTo("GET");
        assertThat(request.getValue().uri().toString())
                .isEqualTo("https://docs.google.com/spreadsheets/d/sheet-123/export?format=csv&gid=42");
        assertThat(request.getValue().headers().firstValue("Authorization")).isEmpty();
        assertThat(request.getValue().headers().firstValue("Cookie")).isEmpty();
        assertThat(request.getValue().timeout()).isPresent();
    }

    @Test
    void defaultTabDoesNotAssumeGidZeroAndCanReadEmptyPublicSheets() {
        respond(response(200, ""));
        var result = reader.read(URL);
        assertThat(result.sourceUrl()).isEqualTo(URL);
        assertThat(result.rows()).isEmpty();
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(request.capture(), anyBodyHandler());
        assertThat(request.getValue().uri().getQuery()).isEqualTo("format=csv");
    }

    @Test
    void parsesBomQuotedCommasEscapedQuotesMultilineTextAndBlankCells() {
        respond(response(200, "\uFEFFname,price,description,quantity\r\n\"Phone, Pro\",\"1,234.50\",\"Line one\r\nLine two with \"\"quotes\"\"\",\r\n"));
        var result = reader.read(URL);
        assertThat(result.rows().get(0).get(0)).isEqualTo("name");
        assertThat(result.rows().get(1)).containsExactly("Phone, Pro", "1,234.50",
                "Line one\r\nLine two with \"quotes\"", "");
    }

    @Test
    void preservesMoneyPrecisionAndLeadingZeroSkusDuringImport() {
        respond(response(200, "sku,name,price\n000001,Phone,90071992547409.91\n"));
        var result = new SheetParser(properties).parse(reader.read(URL).rows(), "NGN");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().sellerSku()).isEqualTo("000001");
        assertThat(result.items().getFirst().priceMinor()).isEqualTo(9007199254740991L);
    }

    @Test
    void followsOnlyGoogleDownloadRedirectsAndUsesCsvFilenameAsTitle() {
        respond(redirect("https://doc-01-sheets.googleusercontent.com/export/download"),
                response(200, "name,price\nPhone,100", Map.of("Content-Type", List.of("application/octet-stream"),
                        "Content-Disposition", List.of("attachment; filename=\"Shop - Inventory.csv\""))));
        var result = reader.read(URL);
        assertThat(result.sheetTitle()).isEqualTo("Shop - Inventory");
        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).sendAsync(requests.capture(), anyBodyHandler());
        assertThat(requests.getAllValues().get(1).uri().getHost()).isEqualTo("doc-01-sheets.googleusercontent.com");
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.headers().firstValue("Authorization")).isEmpty();
            assertThat(request.headers().firstValue("Cookie")).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://127.0.0.1/secret", "http://docs.google.com/export",
            "https://docs.google.com.evil.test/export", "https://evilgoogleusercontent.com/export",
            "https://docs.google.com@evil.test/export", "https://docs.google.com:8443/export"})
    void rejectsUnsafeRedirectsBeforeContactingTheirTargets(String location) {
        respond(redirect(location));
        assertFailure(() -> reader.read(URL), SheetAccessException.Kind.UPSTREAM);
        verify(client).sendAsync(any(HttpRequest.class), anyBodyHandler());
    }

    @Test
    void loginRedirectAndHtmlResponsesAreActionableNonpublicErrors() {
        respond(redirect("https://accounts.google.com/ServiceLogin?continue=private"));
        assertThatThrownBy(() -> reader.read(URL)).isInstanceOfSatisfying(SheetAccessException.class, error -> {
            assertThat(error.getKind()).isEqualTo(SheetAccessException.Kind.NOT_PUBLIC);
            assertThat(error.getMessage()).contains("Anyone with the link", "Viewer");
        });
        respond(response(200, "<html>private-login-page</html>", Map.of("Content-Type", List.of("text/html"))));
        assertFailure(() -> reader.read(URL), SheetAccessException.Kind.NOT_PUBLIC);
    }

    @ParameterizedTest
    @CsvSource({"401,NOT_PUBLIC", "403,NOT_PUBLIC", "400,NOT_FOUND", "404,NOT_FOUND", "429,UPSTREAM", "500,UPSTREAM"})
    void mapsHttpErrorsWithoutLeakingGoogleResponses(int status, String kind) {
        respond(response(status, "private-google-error-body"));
        assertThatThrownBy(() -> reader.read(URL)).isInstanceOfSatisfying(SheetAccessException.class, error -> {
            assertThat(error.getKind()).isEqualTo(SheetAccessException.Kind.valueOf(kind));
            assertThat(error.getMessage()).doesNotContain("private-google-error-body");
        });
    }

    @Test
    void malformedCsvAndUnexpectedContentTypesAreRejected() {
        respond(response(200, "name,price\n\"unclosed,100"));
        assertFailure(() -> reader.read(URL), SheetAccessException.Kind.UPSTREAM);
        respond(response(200, "{}", Map.of("Content-Type", List.of("application/json"))));
        assertFailure(() -> reader.read(URL), SheetAccessException.Kind.UPSTREAM);
    }

    @Test
    void rejectsOversizedRowsColumnsAndResponsesWithoutTruncating() {
        properties.setMaxRows(1);
        respond(response(200, "name,price\nA,1\nB,2"));
        assertFailure(() -> reader.read(URL), SheetAccessException.Kind.LIMIT_EXCEEDED);
        properties.setMaxColumns(2);
        respond(response(200, "name,price,extra"));
        assertFailure(() -> reader.read(URL), SheetAccessException.Kind.LIMIT_EXCEEDED);
        properties.setMaxResponseBytes(4);
        respond(response(200, "name,price"));
        assertFailure(() -> reader.read(URL), SheetAccessException.Kind.LIMIT_EXCEEDED);
    }

    @Test
    void cancelsRequestsAtDeadlineAndSanitizesNetworkFailures() {
        properties.setRequestTimeoutSeconds(1);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        respond(pending);
        assertFailure(() -> reader.read(URL), SheetAccessException.Kind.UPSTREAM);
        assertThat(pending.isCancelled()).isTrue();
        respond(CompletableFuture.failedFuture(new IOException("private transport details")));
        assertThatThrownBy(() -> reader.read(URL)).isInstanceOf(SheetAccessException.class)
                .hasMessageNotContaining("private transport details");
    }

    @Test
    void limitsRedirectLoops() {
        respond(redirect("https://docs.google.com/download"));
        assertFailure(() -> reader.read(URL), SheetAccessException.Kind.UPSTREAM);
        verify(client, times(4)).sendAsync(any(HttpRequest.class), anyBodyHandler());
    }

    @Test
    void invalidSourceUrlsNeverMakeNetworkRequests() {
        assertFailure(() -> reader.read("https://127.0.0.1/internal"), SheetAccessException.Kind.INVALID_URL);
        verifyNoInteractions(client);
    }

    @Test
    void streamingBodyLimitCancelsBeforeBufferingOversizedPayload() {
        var subscriber = new GoogleSheetsReader.LimitedBodySubscriber(4);
        Flow.Subscription subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2, 3})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {4, 5})));
        verify(subscription).cancel();
        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .isInstanceOf(CompletionException.class).hasCauseInstanceOf(SheetAccessException.class);
    }

    @SafeVarargs
    private void respond(CompletableFuture<HttpResponse<byte[]>>... responses) {
        var stub = when(client.sendAsync(any(HttpRequest.class), anyBodyHandler())).thenReturn(responses[0]);
        for (int i = 1; i < responses.length; i++) stub = stub.thenReturn(responses[i]);
    }

    private static HttpResponse.BodyHandler<byte[]> anyBodyHandler() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static CompletableFuture<HttpResponse<byte[]>> redirect(String location) {
        return response(302, "", Map.of("Location", List.of(location)));
    }

    private static CompletableFuture<HttpResponse<byte[]>> response(int status, String body) {
        return response(status, body, Map.of("Content-Type", List.of("text/csv; charset=utf-8")));
    }

    private static CompletableFuture<HttpResponse<byte[]>> response(int status, String body, Map<String, List<String>> headers) {
        return CompletableFuture.completedFuture(new TestResponse(status, body.getBytes(StandardCharsets.UTF_8),
                HttpHeaders.of(headers, (name, value) -> true)));
    }

    private record TestResponse(int statusCode, byte[] body, HttpHeaders headers) implements HttpResponse<byte[]> {
        @Override public HttpRequest request() { return HttpRequest.newBuilder(URI.create("https://docs.google.com")).build(); }
        @Override public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://docs.google.com"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    }

    private static void assertFailure(Runnable action, SheetAccessException.Kind kind) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SheetAccessException.class,
                error -> assertThat(error.getKind()).isEqualTo(kind));
    }
}
