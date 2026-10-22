package dot.kt.priceChecker.entities.catalog;

import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.utils.catalog.CatalogText;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "electrical_appliance_items")
@DiscriminatorValue("ELECTRICAL_APPLIANCE")
public class ElectricalAppliance extends Item {
    @Column(name = "appliance_type", nullable = false, length = 255) private String applianceType;
    @Column(name = "appliance_size", length = 255) private String size;
    @Column(length = 1000) private String features;

    protected ElectricalAppliance() { }

    public ElectricalAppliance(String name, String brand, String model, String applianceType,
            String size, String features, String identityKey) {
        super(name, brand, model, identityKey);
        this.applianceType = CatalogText.text(applianceType);
        this.size = CatalogText.text(size);
        this.features = CatalogText.text(features);
    }

    @Override public ItemDomain getCategory() { return ItemDomain.ELECTRICAL_APPLIANCE; }
    @Override public String getApplianceType() { return applianceType; }
    @Override public String getSize() { return size; }
    @Override public String getFeatures() { return features; }
}
