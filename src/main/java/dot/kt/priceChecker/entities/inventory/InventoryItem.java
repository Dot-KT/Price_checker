package dot.kt.priceChecker.entities.inventory;

import dot.kt.priceChecker.entities.sellers.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_seller_sku", columnNames = {"seller_id", "seller_sku"})
})
public class InventoryItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, updatable = false)
    private Seller seller;

    @Column(name = "seller_sku", nullable = false, updatable = false, length = 128)
    private String sellerSku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String model;

    @Column(length = 4000)
    private String description;

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    private Integer quantity;

    @Column(name = "source_row", nullable = false)
    private int sourceRow;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {
    }

    public InventoryItem(Seller seller, String sku, String name, String model, String description,
                         long priceMinor, String currency, Integer quantity, int sourceRow) {
        this.id = UUID.randomUUID();
        this.seller = seller;
        this.sellerSku = sku;
        this.createdAt = Instant.now();
        update(name, model, description, priceMinor, currency, quantity, sourceRow);
    }

    public void update(String name, String model, String description, long priceMinor,
                       String currency, Integer quantity, int sourceRow) {
        this.name = name;
        this.model = model;
        this.description = description;
        this.priceMinor = priceMinor;
        this.currency = currency;
        this.quantity = quantity;
        this.sourceRow = sourceRow;
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Seller getSeller() {
        return seller;
    }

    public String getSellerSku() {
        return sellerSku;
    }

    public String getName() {
        return name;
    }

    public String getModel() {
        return model;
    }

    public String getDescription() {
        return description;
    }

    public long getPriceMinor() {
        return priceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public int getSourceRow() {
        return sourceRow;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
