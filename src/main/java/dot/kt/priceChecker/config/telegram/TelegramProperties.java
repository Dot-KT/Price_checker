package dot.kt.priceChecker.config.telegram;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.telegram")
public class TelegramProperties {
    private boolean enabled;
    private String botToken = "";
    @Min(60) @Max(86400) private long conversationTimeoutSeconds = 3600;
    @Min(1) @Max(50) private int pollTimeoutSeconds = 25;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public long getConversationTimeoutSeconds() { return conversationTimeoutSeconds; }
    public void setConversationTimeoutSeconds(long seconds) { this.conversationTimeoutSeconds = seconds; }
    public int getPollTimeoutSeconds() { return pollTimeoutSeconds; }
    public void setPollTimeoutSeconds(int seconds) { this.pollTimeoutSeconds = seconds; }
    @AssertTrue(message = "TELEGRAM_BOT_TOKEN is required when Telegram polling is enabled.")
    public boolean isTokenConfiguredWhenEnabled() { return !enabled || botToken != null && !botToken.isBlank(); }
}
