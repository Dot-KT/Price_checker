package dot.kt.priceChecker.utils.catalog;

import java.math.BigDecimal;
import java.util.Currency;

public final class MoneyText {
    private MoneyText() { }
    public static String format(long minor, String currency) {
        return currency + " " + BigDecimal.valueOf(minor, Currency.getInstance(currency).getDefaultFractionDigits()).toPlainString();
    }
}
