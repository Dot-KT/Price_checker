package dot.kt.priceChecker.entities.telegram;

import dot.kt.priceChecker.entities.catalog.Item;
import dot.kt.priceChecker.entities.inventory.InventoryItem;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_users")
public class TelegramUser {
    @Id private UUID id;
    @Column(name = "telegram_id", nullable = false, unique = true) private long telegramId;
    @Column(length = 255) private String username;
    @Column(name = "first_name", length = 255) private String firstName;
    @Column(name = "last_name", length = 255) private String lastName;
    @Column(name = "real_name", length = 255) private String realName;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "selected_item_id") private Item selectedItem;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "selected_listing_id") private InventoryItem selectedListing;
    @Column(name = "selection_json", columnDefinition = "text") private String selectionJson;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected TelegramUser() { }
    public TelegramUser(long telegramId, Instant now) {
        this.id = UUID.randomUUID(); this.telegramId = telegramId; this.createdAt = now; this.updatedAt = now;
    }
    public void updateProfile(String username, String firstName, String lastName, Instant now) {
        this.username = clean(username); this.firstName = clean(firstName); this.lastName = clean(lastName); this.updatedAt = now;
    }
    private static String clean(String value) {
        if (value == null) return null;
        String text = value.replace("\0", "").strip();
        return text.substring(0, Math.min(255, text.length()));
    }
    public void name(String realName, Instant now) { this.realName = clean(realName); this.updatedAt = now; }
    public void select(Item item, InventoryItem listing, String snapshot, Instant now) {
        this.selectedItem = item; this.selectedListing = listing; this.selectionJson = snapshot; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public long getTelegramId() { return telegramId; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getRealName() { return realName; }
    public Item getSelectedItem() { return selectedItem; }
    public InventoryItem getSelectedListing() { return selectedListing; }
    public String getSelectionJson() { return selectionJson; }
    public Instant getUpdatedAt() { return updatedAt; }
}
