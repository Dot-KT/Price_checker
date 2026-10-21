package dot.kt.priceChecker.service.security;

import dot.kt.priceChecker.config.security.AuthenticatedUser;
import dot.kt.priceChecker.config.security.JwtProperties;
import dot.kt.priceChecker.dto.auth.LoginRequest;
import dot.kt.priceChecker.dto.auth.TokenResponse;
import dot.kt.priceChecker.dto.sellers.RegisterSellerRequest;
import dot.kt.priceChecker.dto.sellers.SellerResponse;
import dot.kt.priceChecker.entities.sellers.Seller;
import dot.kt.priceChecker.entities.users.AppUser;
import dot.kt.priceChecker.exceptions.common.ApiException;
import dot.kt.priceChecker.repo.sellers.SellerRepository;
import dot.kt.priceChecker.repo.users.UserRepository;
import dot.kt.priceChecker.utils.common.CurrencyCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwords;
    private final UserRepository users;
    private final SellerRepository sellers;
    private final JwtEncoder jwt;
    private final JwtProperties properties;
    private final CurrentUserService currentUser;

    public AuthenticationService(AuthenticationManager authenticationManager, PasswordEncoder passwords,
            UserRepository users, SellerRepository sellers, JwtEncoder jwt, JwtProperties properties,
            CurrentUserService currentUser) {
        this.authenticationManager = authenticationManager;
        this.passwords = passwords;
        this.users = users;
        this.sellers = sellers;
        this.jwt = jwt;
        this.properties = properties;
        this.currentUser = currentUser;
    }

    @Transactional
    public SellerResponse registerSeller(RegisterSellerRequest request) {
        String email = AppUser.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_IN_USE", "An account with this email already exists.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password must not exceed 72 UTF-8 bytes.");
        }
        String currency = CurrencyCode.validate(request.defaultCurrency() == null ? "NGN" : request.defaultCurrency());
        // Role is assigned on the server, never accepted from registration input.
        Seller seller = new Seller(email, passwords.encode(request.password()), request.displayName().strip(), currency);
        return SellerResponse.from(sellers.saveAndFlush(seller));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid credentials.");
        }
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        var user = (AuthenticatedUser) authentication.getPrincipal();
        Instant issued = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(user.id().toString()).issuedAt(issued)
                .expiresAt(issued.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES))
                .id(UUID.randomUUID().toString()).claim("ver", user.tokenVersion()).build();
        String token = jwt.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.accessTokenMinutes() * 60);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void logout() {
        users.revokeTokens(currentUser.id());
    }
}
