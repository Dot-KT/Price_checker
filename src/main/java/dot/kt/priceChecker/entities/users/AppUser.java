package dot.kt.priceChecker.entities.users;

import dot.kt.priceChecker.enums.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Role role;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "token_version", nullable = false) private long tokenVersion;
    @Version @Column(nullable = false) private long revision;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AppUser() { }

    public AppUser(String email, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public long getTokenVersion() { return tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void updateEmail(String email) { this.email = normalizeEmail(email); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
