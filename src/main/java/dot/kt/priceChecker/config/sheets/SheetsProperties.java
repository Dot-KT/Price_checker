package dot.kt.priceChecker.config.sheets;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.sheets")
public class SheetsProperties {
    @Min(1) @Max(100_000)
    private int maxRows = 10_000;
    @Min(2) @Max(1_000)
    private int maxColumns = 64;
    @Min(1_024) @Max(67_108_864)
    private int maxResponseBytes = 8_388_608;
    @Min(1) @Max(60)
    private int connectTimeoutSeconds = 5;
    @Min(1) @Max(120)
    private int requestTimeoutSeconds = 20;

    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int value) { maxRows = value; }
    public int getMaxColumns() { return maxColumns; }
    public void setMaxColumns(int value) { maxColumns = value; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int value) { maxResponseBytes = value; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int value) { connectTimeoutSeconds = value; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int value) { requestTimeoutSeconds = value; }
}
