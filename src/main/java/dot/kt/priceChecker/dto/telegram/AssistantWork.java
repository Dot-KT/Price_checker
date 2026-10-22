package dot.kt.priceChecker.dto.telegram;

import dot.kt.priceChecker.enums.catalog.GadgetType;
import dot.kt.priceChecker.enums.catalog.ItemDomain;
import java.util.List;
import java.util.UUID;

public record AssistantWork(long updateId, UUID conversationId, ItemDomain domain, GadgetType gadgetType,
        String question, List<String> previousItems, UUID forcedItemId, QuestionIntent previousIntent, List<UUID> resolvedItemIds) { }
