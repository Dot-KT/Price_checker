package dot.kt.priceChecker.dto.sellers;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterSellerRequest(
        @NotBlank @Email @Size(max = 254) @Schema(example = "seller@example.com") String email,
        @NotBlank @Size(min = 12, max = 72) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, example = "choose-your-own-password") String password,
        @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\x00]*", message = "Must not contain NUL characters.") String displayName,
        @Pattern(regexp = "[A-Za-z]{3}") String defaultCurrency) {
}
