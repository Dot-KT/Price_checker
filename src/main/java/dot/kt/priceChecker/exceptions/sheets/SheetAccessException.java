package dot.kt.priceChecker.exceptions.sheets;

public class SheetAccessException extends RuntimeException {
    public enum Kind { INVALID_URL, NOT_PUBLIC, NOT_FOUND, UPSTREAM, LIMIT_EXCEEDED }

    private final Kind kind;

    public SheetAccessException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() { return kind; }
}
