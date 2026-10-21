package dot.kt.priceChecker.config.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(@NotBlank String issuer, @NotBlank String audience,
        @NotBlank String signingKeyPath, @Min(1) @Max(60) long accessTokenMinutes) {
}
