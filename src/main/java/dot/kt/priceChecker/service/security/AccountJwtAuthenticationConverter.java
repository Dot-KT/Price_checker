package dot.kt.priceChecker.service.security;

import dot.kt.priceChecker.config.security.AuthenticatedUser;
import dot.kt.priceChecker.repo.users.UserRepository;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;

    public AccountJwtAuthenticationConverter(UserRepository users) { this.users = users; }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id;
        Long version;
        try {
            id = UUID.fromString(jwt.getSubject());
            version = jwt.getClaim("ver") instanceof Number number ? number.longValue() : null;
        } catch (IllegalArgumentException exception) {
            throw new InvalidBearerTokenException("Invalid access token.");
        }
        var user = users.findById(id).orElseThrow(() -> new InvalidBearerTokenException("Invalid access token."));
        if (!user.isEnabled() || version == null || version != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Invalid access token.");
        }
        var identity = new AuthenticatedUser(user);
        identity.eraseCredentials();
        // Permissions always come from the live account, never from client token claims.
        return UsernamePasswordAuthenticationToken.authenticated(identity, null, identity.getAuthorities());
    }
}
