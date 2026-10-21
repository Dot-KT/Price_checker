package dot.kt.priceChecker.config.security;

import dot.kt.priceChecker.entities.users.AppUser;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class AuthenticatedUser extends User {
    private final UUID id;
    private final long tokenVersion;

    public AuthenticatedUser(AppUser user) {
        super(user.getEmail(), user.getPasswordHash(), user.isEnabled(), true, true, true, authorities(user));
        this.id = user.getId();
        this.tokenVersion = user.getTokenVersion();
    }

    public UUID id() { return id; }
    public long tokenVersion() { return tokenVersion; }

    private static java.util.List<GrantedAuthority> authorities(AppUser user) {
        var authorities = new ArrayList<GrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        user.getRole().permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return authorities;
    }
}
