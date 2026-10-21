package dot.kt.priceChecker.utils.common;

import dot.kt.priceChecker.exceptions.common.ApiException;
import java.util.Currency;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public final class CurrencyCode {
    private CurrencyCode() { }
    public static String validate(String value) {
        String code = value.toUpperCase(Locale.ROOT);
        try {
            if (Currency.getInstance(code).getDefaultFractionDigits() < 0) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY", "Use a supported ISO currency, such as NGN.");
        }
        return code;
    }
}
