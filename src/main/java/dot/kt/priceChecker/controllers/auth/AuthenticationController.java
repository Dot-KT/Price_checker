package dot.kt.priceChecker.controllers.auth;

import dot.kt.priceChecker.dto.auth.LoginRequest;
import dot.kt.priceChecker.dto.auth.TokenResponse;
import dot.kt.priceChecker.dto.sellers.RegisterSellerRequest;
import dot.kt.priceChecker.dto.sellers.SellerResponse;
import dot.kt.priceChecker.service.security.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Seller-only registration, account login, and token revocation")
public class AuthenticationController {
    private final AuthenticationService authentication;

    public AuthenticationController(AuthenticationService authentication) { this.authentication = authentication; }

    @PostMapping(value = {"/sellers/register", "/register"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register a seller", description = "Always creates a SELLER account. The /register path is a compatibility alias.")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Seller account created")
    public ResponseEntity<SellerResponse> register(@Valid @RequestBody RegisterSellerRequest request) {
        return ResponseEntity.created(URI.create("/api/v1/seller/me")).body(authentication.registerSeller(request));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sign in", description = "Returns a short-lived bearer token for any enabled account. Paste accessToken into Authorize.")
    @SecurityRequirements
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Pragma", "no-cache")
                .body(authentication.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out on all devices", description = "Immediately revokes all outstanding access tokens for your account.")
    @ApiResponse(responseCode = "204", description = "Access tokens revoked")
    public ResponseEntity<Void> logout() {
        authentication.logout();
        return ResponseEntity.noContent().build();
    }
}
