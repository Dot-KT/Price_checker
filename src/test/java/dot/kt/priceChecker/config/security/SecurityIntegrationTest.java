package dot.kt.priceChecker.config.security;

import dot.kt.priceChecker.dto.common.PageQuery;
import dot.kt.priceChecker.dto.sellers.UpdateSellerRequest;
import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import dot.kt.priceChecker.entities.users.AppUser;
import dot.kt.priceChecker.enums.security.Role;
import dot.kt.priceChecker.repo.imports.SheetImportRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.repo.sellers.SellerRepository;
import dot.kt.priceChecker.repo.users.UserRepository;
import dot.kt.priceChecker.service.security.AccountService;
import dot.kt.priceChecker.service.sellers.SellerService;
import dot.kt.priceChecker.service.sheets.SheetReader;
import dot.kt.priceChecker.support.TestTokens;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
    private static final String PASSWORD = "a-valid-account-password";
    private static final String URL = "https://docs.google.com/spreadsheets/d/seller-sheet-123/edit#gid=0";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired SellerRepository sellers;
    @Autowired InventoryItemRepository inventory;
    @Autowired SheetImportRepository imports;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired SellerService sellerService;
    @Autowired AccountService accountService;
    @MockitoBean SheetReader reader;

    @BeforeEach
    void clearDatabase() {
        imports.deleteAllInBatch();
        inventory.deleteAllInBatch();
        sellers.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void registrationAlwaysCreatesSellerAndCannotRequestPrivileges() throws Exception {
        mvc.perform(post("/api/v1/auth/sellers/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "SELLER@example.com", "password", PASSWORD,
                                "displayName", "Shop", "role", "ADMIN", "authorities", List.of("project:write")))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(users.findByEmail("seller@example.com").orElseThrow().getRole()).isEqualTo(Role.SELLER);
        mvc.perform(get("/api/v1/account/me").with(bearer("seller@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SELLER"));
        mvc.perform(post("/api/v1/auth/admin/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsInvalidCredentialsAndReturnsUncacheableShortLivedTokens() throws Exception {
        seed("user@example.com", Role.USER);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "USER@example.com", "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        for (String email : List.of("user@example.com", "missing@example.com")) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void managerCanReadEveryCurrentResourceButCannotWrite() throws Exception {
        UUID sellerId = registerSeller("seller@example.com");
        seed("manager@example.com", Role.MANAGER);
        RequestPostProcessor manager = bearer("manager@example.com");
        String target = "/api/v1/management/sellers/" + sellerId;
        for (String path : List.of("/api/v1/management/accounts", "/api/v1/management/sellers", target,
                target + "/inventory", target + "/inventory/imports")) {
            mvc.perform(get(path).with(manager)).andExpect(status().isOk());
        }
        mvc.perform(put(target).with(manager).contentType(MediaType.APPLICATION_JSON).content(profile()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(delete(target).with(manager)).andExpect(status().isForbidden());
        mvc.perform(put(target + "/sheet").with(manager).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL)))).andExpect(status().isForbidden());
        mvc.perform(delete(target + "/sheet").with(manager)).andExpect(status().isForbidden());
        mvc.perform(post(target + "/inventory/imports").with(manager).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL)))).andExpect(status().isForbidden());
        mvc.perform(post(target + "/inventory/imports/saved").with(manager)).andExpect(status().isForbidden());
        mvc.perform(post(target + "/inventory/imports/" + UUID.randomUUID() + "/confirm").with(manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/seller/me").with(manager).contentType(MediaType.APPLICATION_JSON).content(profile()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reader);
        assertThat(sellers.existsById(sellerId)).isTrue();
    }

    @Test
    void adminCanManageAnotherSellersProfileInventoryAndImports() throws Exception {
        UUID sellerId = registerSeller("seller@example.com");
        seed("admin@example.com", Role.ADMIN);
        seed("manager@example.com", Role.MANAGER);
        RequestPostProcessor admin = bearer("admin@example.com");
        String target = "/api/v1/management/sellers/" + sellerId;
        mvc.perform(put(target).with(admin).contentType(MediaType.APPLICATION_JSON).content(profile()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Updated shop"));
        mvc.perform(put(target + "/sheet").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL)))).andExpect(status().isOk());
        when(reader.read(URL)).thenReturn(new SheetSnapshot(URL, "Inventory",
                List.of(List.<Object>of("sku", "name", "price"), List.<Object>of("PHONE-1", "Phone", "100"))));
        mvc.perform(get(target + "/sheet").with(bearer("manager@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[1][1]").value("Phone"));
        var preview = mvc.perform(post(target + "/inventory/imports/saved").with(admin))
                .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(preview.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get(target + "/inventory/imports/" + id).with(bearer("manager@example.com")))
                .andExpect(status().isOk());
        mvc.perform(post(target + "/inventory/imports/" + id + "/confirm").with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get(target + "/inventory").with(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Phone"));
        mvc.perform(delete(target + "/sheet").with(admin)).andExpect(status().isNoContent());
        mvc.perform(delete(target).with(admin)).andExpect(status().isNoContent());
        assertThat(users.existsById(sellerId)).isFalse();
        assertThat(inventory.count()).isZero();
        assertThat(imports.count()).isZero();
    }

    @Test
    void sellerAndUserCannotAccessManagementOrOtherAccounts() throws Exception {
        UUID sellerId = registerSeller("seller@example.com");
        seed("user@example.com", Role.USER);
        for (String email : List.of("seller@example.com", "user@example.com")) {
            RequestPostProcessor token = bearer(email);
            mvc.perform(get("/api/v1/account/me").with(token)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email));
            mvc.perform(get("/api/v1/management/accounts").with(token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/management/sellers/" + sellerId).with(token)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/seller/me").with(bearer("user@example.com"))).andExpect(status().isForbidden());
    }

    @Test
    void servicesEnforceOwnershipEvenWhenCalledWithoutAController() throws Exception {
        UUID first = registerSeller("first@example.com");
        UUID second = registerSeller("second@example.com");
        AppUser seller = users.findById(first).orElseThrow();
        AppUser manager = seed("manager@example.com", Role.MANAGER);
        try {
            authenticate(seller);
            assertThatThrownBy(() -> sellerService.get(second)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> sellerService.update(second, new UpdateSellerRequest("other@example.com", "Other", "NGN")))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> accountService.list(new PageQuery(0, 50))).isInstanceOf(AccessDeniedException.class);
            authenticate(manager);
            assertThat(sellerService.get(second).id()).isEqualTo(second);
            assertThatThrownBy(() -> sellerService.delete(second)).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void logoutRevokesEveryOutstandingTokenAndAllowsNewLogin() throws Exception {
        seed("user@example.com", Role.USER);
        var first = bearer("user@example.com");
        var second = bearer("user@example.com");
        mvc.perform(post("/api/v1/auth/logout").with(first)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/account/me").with(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/me").with(second)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/me").with(bearer("user@example.com"))).andExpect(status().isOk());
    }

    @Test
    void disablingAccountImmediatelyRejectsTokensAndLogin() throws Exception {
        AppUser user = seed("user@example.com", Role.USER);
        var token = bearer(user.getEmail());
        user.setEnabled(false);
        users.saveAndFlush(user);
        mvc.perform(get("/api/v1/account/me").with(token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangesTakeEffectOnExistingTokens() throws Exception {
        AppUser user = seed("admin@example.com", Role.ADMIN);
        var token = bearer(user.getEmail());
        mvc.perform(get("/api/v1/management/accounts").with(token)).andExpect(status().isOk());
        jdbc.update("UPDATE app_users SET role = 'USER', revision = revision + 1 WHERE id = ?", user.getId());
        mvc.perform(get("/api/v1/management/accounts").with(token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/account/me").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void malformedExpiredWrongIssuerAndWrongAudienceTokensAreRejected() throws Exception {
        AppUser user = seed("user@example.com", Role.USER);
        mvc.perform(get("/api/v1/account/me").with(TestTokens.token("not-a-jwt")))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        for (String invalid : List.of(signed(user, "wrong-issuer", "price-checker-api", 300),
                signed(user, "price-checker", "wrong-audience", 300),
                signed(user, "price-checker", "price-checker-api", -120))) {
            mvc.perform(get("/api/v1/account/me").with(TestTokens.token(invalid))).andExpect(status().isUnauthorized());
        }
        String valid = signed(user, "price-checker", "price-checker-api", 300);
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/v1/account/me").with(TestTokens.token(String.join(".", parts))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void basicCredentialsAndBrowserSessionsDoNotAuthenticateRequests() throws Exception {
        seed("user@example.com", Role.USER);
        mvc.perform(get("/api/v1/account/me").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .httpBasic("user@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/me").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "untrusted-session")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyBcryptHashesStillAuthenticate() throws Exception {
        users.saveAndFlush(new AppUser("legacy@example.com", new BCryptPasswordEncoder().encode(PASSWORD), Role.USER));
        mvc.perform(get("/api/v1/account/me").with(bearer("legacy@example.com"))).andExpect(status().isOk());
    }

    private UUID registerSeller(String email) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/sellers/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "displayName", "Shop"))))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }

    private AppUser seed(String email, Role role) {
        return users.saveAndFlush(new AppUser(email, passwords.encode(PASSWORD), role));
    }

    private RequestPostProcessor bearer(String email) throws Exception {
        return TestTokens.bearer(mvc, json, email, PASSWORD);
    }

    private String profile() throws Exception {
        return json.writeValueAsString(Map.of("email", "seller@example.com", "displayName", "Updated shop", "defaultCurrency", "NGN"));
    }

    private void authenticate(AppUser user) {
        var identity = new AuthenticatedUser(user);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity, null, identity.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private String signed(AppUser user, String issuer, String audience, long seconds) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(user.getId().toString())
                .issuedAt(now.minusSeconds(300)).expiresAt(now.plusSeconds(seconds)).claim("ver", user.getTokenVersion()).build();
        return jwt.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
