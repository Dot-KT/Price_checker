package dot.kt.priceChecker.entities.catalog;

import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.enums.catalog.GadgetType;
import dot.kt.priceChecker.utils.catalog.CatalogText;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "items")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "category", length = 32)
public abstract class Item {
    @Id private UUID id;
    @Column(nullable = false, length = 255) private String name;
    @Column(nullable = false, length = 255) private String brand;
    @Column(name = "brand_key", nullable = false, length = 255) private String brandKey;
    @Column(length = 255) private String model;
    @Column(name = "identity_key", nullable = false, unique = true, length = 64) private String identityKey;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Item() { }

    protected Item(String name, String brand, String model, String identityKey) {
        this.id = UUID.randomUUID();
        this.name = CatalogText.text(name);
        this.brand = CatalogText.brand(brand);
        this.brandKey = CatalogText.key(this.brand);
        this.model = CatalogText.text(model);
        this.identityKey = identityKey;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getBrandKey() { return brandKey; }
    public String getModel() { return model; }
    public abstract ItemDomain getCategory();
    public GadgetType getGadgetType() { return null; }
    public String getSeries() { return null; }
    public String getApplianceType() { return null; }
    public String getSize() { return null; }
    public String getFeatures() { return null; }
}
