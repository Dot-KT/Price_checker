package dot.kt.priceChecker.dto.telegram;

import java.util.List;
import java.util.UUID;

public record AssistantResult(QuestionIntent intent, String text, List<UUID> itemIds,
        List<UUID> candidateIds, boolean needsChoice, String richHtml) {
    public AssistantResult(QuestionIntent intent, String text, List<UUID> itemIds, List<UUID> candidateIds, boolean needsChoice) {
        this(intent, text, itemIds, candidateIds, needsChoice, null);
    }
}
