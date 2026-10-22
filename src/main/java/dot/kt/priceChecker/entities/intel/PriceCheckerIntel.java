package dot.kt.priceChecker.entities.intel;

import dot.kt.priceChecker.entities.catalog.Item;
import dot.kt.priceChecker.entities.users.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_checker_intel", uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "currency"}))
public class PriceCheckerIntel {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "item_id", nullable = false) private Item item;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "price_minor", nullable = false) private long priceMinor;
    @Column(length = 2000) private String notes;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "updated_by", nullable = false) private AppUser updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected PriceCheckerIntel() { }
    public PriceCheckerIntel(Item item, String currency) { this.id = UUID.randomUUID(); this.item = item; this.currency = currency; }
    public void update(long priceMinor, String notes, AppUser admin, Instant now) {
        this.priceMinor = priceMinor; this.notes = notes; this.updatedBy = admin; this.updatedAt = now;
    }
    public Item getItem() { return item; }
    public String getCurrency() { return currency; }
    public long getPriceMinor() { return priceMinor; }
    public String getNotes() { return notes; }
    public Instant getUpdatedAt() { return updatedAt; }
}
