package dot.kt.priceChecker.dto.users;

import dot.kt.priceChecker.entities.users.AppUser;
import dot.kt.priceChecker.enums.security.Role;
import java.util.Set;
import java.util.UUID;

public record AccountResponse(UUID id, String email, Role role, boolean enabled, Set<String> permissions) {
    public static AccountResponse from(AppUser user) {
        return new AccountResponse(user.getId(), user.getEmail(), user.getRole(), user.isEnabled(), user.getRole().permissions());
    }
}
