package dot.kt.priceChecker.dto.telegram;

public record TelegramIncoming(long updateId, long telegramId, long chatId, String username,
        String firstName, String lastName, String text, String callbackData, String callbackQueryId, boolean privateChat) { }
