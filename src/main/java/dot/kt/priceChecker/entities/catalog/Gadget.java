package dot.kt.priceChecker.entities.catalog;

import dot.kt.priceChecker.enums.catalog.GadgetType;
import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.utils.catalog.CatalogText;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "gadget_items")
@DiscriminatorValue("GADGET")
public class Gadget extends Item {
    @Column(length = 255) private String series;
    @Enumerated(EnumType.STRING)
    @Column(name = "gadget_type", nullable = false, length = 32) private GadgetType gadgetType;

    protected Gadget() { }

    public Gadget(String name, String brand, String model, String series, GadgetType gadgetType, String identityKey) {
        super(name, brand, model, identityKey);
        this.series = CatalogText.text(series);
        this.gadgetType = gadgetType;
    }

    @Override public ItemDomain getCategory() { return ItemDomain.GADGET; }
    @Override public String getSeries() { return series; }
    @Override public GadgetType getGadgetType() { return gadgetType; }
}
