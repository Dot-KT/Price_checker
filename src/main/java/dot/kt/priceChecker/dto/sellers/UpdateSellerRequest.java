package dot.kt.priceChecker.dto.sellers;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSellerRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\x00]*", message = "Must not contain NUL characters.") String displayName,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String defaultCurrency) {
}
