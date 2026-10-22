package dot.kt.priceChecker.dto.telegram;

import java.util.ArrayList;
import java.util.List;

public record BotReply(String text, List<List<Button>> buttons, String richHtml) {
    public BotReply(String text, List<List<Button>> buttons) { this(text, buttons, null); }
    public record Button(String text, String callbackData, String url) {
        public static Button action(String text, String data) { return new Button(text, data, null); }
        public static Button link(String text, String url) { return new Button(text, null, url); }
    }
    public BotReply { buttons = buttons == null ? List.of() : List.copyOf(buttons); }
    public static BotReply text(String text) { return new BotReply(text, List.of()); }
    public static List<BotReply> split(List<BotReply> replies) {
        List<BotReply> chunks = new ArrayList<>();
        for (BotReply reply : replies) {
            if (reply.richHtml() != null && reply.richHtml().length() <= 30000) { chunks.add(reply); continue; }
            String remaining = reply.text();
            while (remaining.codePointCount(0, remaining.length()) > 3500) {
                int end = remaining.offsetByCodePoints(0, 3500);
                int newline = remaining.lastIndexOf('\n', end);
                if (newline > end / 2) end = newline;
                chunks.add(text(remaining.substring(0, end))); remaining = remaining.substring(end);
            }
            chunks.add(new BotReply(remaining.isBlank() ? "Please choose an option." : remaining, reply.buttons()));
        }
        return List.copyOf(chunks);
    }
}
