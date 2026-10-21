package dot.kt.priceChecker.service.security;

import dot.kt.priceChecker.config.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    public AuthenticatedUser current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return user;
    }

    public UUID id() { return current().id(); }

    public boolean hasAuthority(String permission) {
        return current().getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(permission));
    }
}
