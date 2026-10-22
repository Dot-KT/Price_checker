package dot.kt.priceChecker.config.telegram;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.ollama")
public class OllamaAssistantProperties {
    @NotBlank private String baseUrl = "http://localhost:11434";
    @NotBlank private String model = "gemma4:e2b-it-q4_K_M";
    @Min(5) @Max(180) private int timeoutSeconds = 120;
    private boolean structuredOutput = true;
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int seconds) { this.timeoutSeconds = seconds; }
    public boolean isStructuredOutput() { return structuredOutput; }
    public void setStructuredOutput(boolean structuredOutput) { this.structuredOutput = structuredOutput; }
}
