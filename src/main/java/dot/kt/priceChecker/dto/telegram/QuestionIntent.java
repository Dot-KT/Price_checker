package dot.kt.priceChecker.dto.telegram;

import java.util.List;

public record QuestionIntent(List<String> items, boolean price, boolean details, boolean comparison,
        String currency, String condition, String clarification, boolean wrongDomain) { }
