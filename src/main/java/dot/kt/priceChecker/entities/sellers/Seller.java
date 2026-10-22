package dot.kt.priceChecker.entities.sellers;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import dot.kt.priceChecker.entities.users.AppUser;
import dot.kt.priceChecker.enums.security.Role;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "sellers")
public class Seller {

    @Id
    private UUID id;

    @OneToOne(optional = false, cascade = CascadeType.ALL)
    @MapsId
    @JoinColumn(name = "id")
    private AppUser user;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(name = "google_sheet_url", length = 2048)
    private String googleSheetUrl;

    @Column(name = "inventory_revision", nullable = false)
    private long inventoryRevision;

    protected Seller() {
    }

    public Seller(String email, String passwordHash, String displayName, String defaultCurrency) {
        this(new AppUser(email, passwordHash, Role.SELLER), displayName, defaultCurrency);
    }

    public Seller(AppUser user, String displayName, String defaultCurrency) {
        this.user = user;
        this.id = user.getId();
        this.displayName = displayName;
        this.defaultCurrency = defaultCurrency.trim().toUpperCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getPasswordHash() {
        return user.getPasswordHash();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public Instant getCreatedAt() {
        return user.getCreatedAt();
    }

    public String getGoogleSheetUrl() {
        return googleSheetUrl;
    }

    public long getInventoryRevision() {
        return inventoryRevision;
    }

    public void advanceInventoryRevision() {
        inventoryRevision = Math.incrementExact(inventoryRevision);
    }

    public void updateProfile(String email, String displayName, String defaultCurrency) {
        this.user.updateEmail(email);
        this.displayName = displayName.strip();
        this.defaultCurrency = defaultCurrency.toUpperCase(Locale.ROOT);
    }

    public void setGoogleSheetUrl(String googleSheetUrl) {
        this.googleSheetUrl = googleSheetUrl;
    }
}
