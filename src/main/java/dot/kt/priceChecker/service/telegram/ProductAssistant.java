package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.dto.telegram.*;
import java.util.List;

public interface ProductAssistant {
    QuestionIntent identify(AssistantWork work);
    SpecificationReport explain(AssistantWork work, List<String> exactItems);
}
