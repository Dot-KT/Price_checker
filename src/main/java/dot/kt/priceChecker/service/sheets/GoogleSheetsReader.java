package dot.kt.priceChecker.service.sheets;

import dot.kt.priceChecker.config.sheets.SheetsProperties;
import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import dot.kt.priceChecker.exceptions.sheets.SheetAccessException;
import dot.kt.priceChecker.utils.sheets.GoogleSheetUrl;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class GoogleSheetsReader implements SheetReader {
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final int MAX_REDIRECTS = 3;
    private final SheetsProperties properties;
    private final HttpClient client;

    @Autowired
    public GoogleSheetsReader(SheetsProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    GoogleSheetsReader(SheetsProperties properties, HttpClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public SheetSnapshot read(String url) {
        GoogleSheetUrl source = GoogleSheetUrl.parse(url);
        URI download = URI.create("https://docs.google.com/spreadsheets/d/" + source.spreadsheetId()
                + "/export?format=csv" + (source.gid() == null ? "" : "&gid=" + source.gid()));
        var response = download(download);
        byte[] bytes = response.body();
        if (bytes.length > properties.getMaxResponseBytes()) throw sizeLimit();
        String filename = filename(response);
        String contentType = response.headers().firstValue("Content-Type").orElse("")
                .toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        if (contentType.equals("text/html") || contentType.equals("application/xhtml+xml")) throw notPublic();
        boolean csv = contentType.equals("text/csv") || contentType.equals("application/csv")
                || contentType.equals("text/comma-separated-values")
                || ((contentType.equals("application/octet-stream") || contentType.equals("text/plain"))
                    && filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".csv"));
        if (!csv) throw failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets did not return a CSV download.");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            List<List<Object>> rows = new ArrayList<>();
            try (var parser = CSVFormat.RFC4180.parse(new StringReader(text))) {
                for (CSVRecord record : parser) {
                    if (rows.size() >= properties.getMaxRows() + 1) {
                        throw failure(SheetAccessException.Kind.LIMIT_EXCEEDED,
                                "The selected tab exceeds the maximum of " + properties.getMaxRows() + " data rows.");
                    }
                    if (record.size() > properties.getMaxColumns()) {
                        throw failure(SheetAccessException.Kind.LIMIT_EXCEEDED,
                                "The selected tab exceeds the maximum of " + properties.getMaxColumns() + " columns.");
                    }
                    List<Object> row = new ArrayList<>(record.size());
                    record.forEach(row::add);
                    rows.add(row);
                }
            }
            return new SheetSnapshot(source.canonicalSourceUrl(), title(filename), rows);
        } catch (IOException | UncheckedIOException | IllegalArgumentException exception) {
            throw failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets returned invalid CSV data.");
        }
    }

    private HttpResponse<byte[]> download(URI initial) {
        URI current = initial;
        long deadline = System.nanoTime() + Duration.ofSeconds(properties.getRequestTimeoutSeconds()).toNanos();
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw timedOut();
            // Never attach Authorization headers or cookies to public spreadsheet requests.
            HttpRequest request = HttpRequest.newBuilder(current).timeout(Duration.ofNanos(remaining))
                    .header("Accept", "text/csv").GET().build();
            var response = send(request, remaining);
            if (REDIRECTS.contains(response.statusCode())) {
                if (redirects == MAX_REDIRECTS) {
                    throw failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets returned too many redirects.");
                }
                String location = response.headers().firstValue("Location").orElse("");
                if (location.isBlank()) throw failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets returned an invalid redirect.");
                URI next;
                try { next = current.resolve(location); }
                catch (IllegalArgumentException exception) {
                    throw failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets returned an invalid redirect.");
                }
                if ("accounts.google.com".equalsIgnoreCase(next.getHost())
                        || "consent.google.com".equalsIgnoreCase(next.getHost())) throw notPublic();
                if (!allowedDownload(next)) {
                    throw failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets returned an unsupported download redirect.");
                }
                current = next;
                continue;
            }
            switch (response.statusCode()) {
                case 200 -> { return response; }
                case 401, 403 -> throw notPublic();
                case 400, 404 -> throw failure(SheetAccessException.Kind.NOT_FOUND,
                        "The spreadsheet or selected tab was not found, or is not publicly accessible. Check the link and sharing settings.");
                case 429 -> throw failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets is rate limiting requests. Try again later.");
                default -> throw failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets could not read this spreadsheet. Try again later.");
            }
        }
        throw failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets could not download this spreadsheet.");
    }

    private HttpResponse<byte[]> send(HttpRequest request, long remainingNanos) {
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request,
                ignored -> new LimitedBodySubscriber(properties.getMaxResponseBytes()));
        try {
            return pending.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw timedOut();
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(SheetAccessException.Kind.UPSTREAM, "The Google Sheets read was interrupted. Try again.");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            while (cause != null) {
                if (cause instanceof SheetAccessException accessException) throw accessException;
                cause = cause.getCause();
            }
            throw failure(SheetAccessException.Kind.UPSTREAM, "Unable to contact Google Sheets. Try again later.");
        }
    }

    private static boolean allowedDownload(URI uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null && uri.getPort() == -1
                && host != null && (host.equalsIgnoreCase("docs.google.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".googleusercontent.com"));
    }

    private static String filename(HttpResponse<?> response) {
        try {
            return response.headers().firstValue("Content-Disposition")
                    .map(header -> ContentDisposition.parse(header).getFilename()).orElse(null);
        } catch (IllegalArgumentException exception) { return null; }
    }

    private static String title(String filename) {
        String value = filename == null ? "Public spreadsheet" : filename.strip();
        if (value.toLowerCase(Locale.ROOT).endsWith(".csv")) value = value.substring(0, value.length() - 4).strip();
        if (value.isBlank()) value = "Public spreadsheet";
        return value.codePoints().limit(255).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    private static SheetAccessException notPublic() {
        return failure(SheetAccessException.Kind.NOT_PUBLIC,
                "The spreadsheet is not publicly readable. Choose Share > General access > Anyone with the link > Viewer, and allow viewers to download. Then try again.");
    }
    private static SheetAccessException timedOut() {
        return failure(SheetAccessException.Kind.UPSTREAM, "Google Sheets timed out. Try again later.");
    }
    private static SheetAccessException sizeLimit() {
        return failure(SheetAccessException.Kind.LIMIT_EXCEEDED, "The spreadsheet response exceeds the configured size limit.");
    }
    private static SheetAccessException failure(SheetAccessException.Kind kind, String message) {
        return new SheetAccessException(kind, message);
    }

    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedBodySubscriber(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) {
            if (subscription != null) { value.cancel(); return; }
            subscription = value;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> chunks) {
            if (body.isDone()) return;
            for (ByteBuffer chunk : chunks) {
                if (chunk.remaining() > limit - buffer.size()) {
                    subscription.cancel();
                    body.completeExceptionally(failure(SheetAccessException.Kind.LIMIT_EXCEEDED,
                            "The spreadsheet response exceeds the configured size limit."));
                    return;
                }
                byte[] bytes = new byte[chunk.remaining()];
                chunk.get(bytes);
                buffer.writeBytes(bytes);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(buffer.toByteArray()); }
    }
}
