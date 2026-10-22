package dot.kt.priceChecker.service.telegram;

import org.springframework.stereotype.Service;

@Service
public class TelegramConversationService {
    private final TelegramConversationStore store;
    private final BotInformationService information;
    public TelegramConversationService(TelegramConversationStore store, BotInformationService information) {
        this.store = store; this.information = information;
    }
    public void process(long updateId) {
        var work = store.prepare(updateId);
        if (work != null) store.complete(work, information.answer(work));
    }
}
