package dot.kt.priceChecker.service.catalog;

import dot.kt.priceChecker.dto.catalog.CatalogAttributes;
import dot.kt.priceChecker.dto.catalog.CatalogOptions;
import dot.kt.priceChecker.dto.catalog.CatalogQuery;
import dot.kt.priceChecker.dto.catalog.CreateItemRequest;
import dot.kt.priceChecker.dto.catalog.ItemResponse;
import dot.kt.priceChecker.dto.catalog.OfferResponse;
import dot.kt.priceChecker.dto.common.PageQuery;
import dot.kt.priceChecker.dto.common.PageResponse;
import dot.kt.priceChecker.entities.catalog.ElectricalAppliance;
import dot.kt.priceChecker.entities.catalog.Item;
import dot.kt.priceChecker.entities.catalog.Gadget;
import dot.kt.priceChecker.enums.catalog.GadgetType;
import dot.kt.priceChecker.entities.inventory.InventoryItem;
import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.exceptions.common.ApiException;
import dot.kt.priceChecker.repo.catalog.ItemRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.utils.catalog.CatalogText;
import dot.kt.priceChecker.utils.common.CurrencyCode;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final ItemRepository items;
    private final InventoryItemRepository inventory;

    public CatalogService(ItemRepository items, InventoryItemRepository inventory) {
        this.items = items;
        this.inventory = inventory;
    }

    @Transactional
    @PreAuthorize("hasAuthority('project:write')")
    public ItemResponse create(CreateItemRequest request) {
        return ItemResponse.from(resolve(request.name(), request.model(), request.attributes()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('seller:write') or hasAuthority('project:write')")
    public Item resolve(String name, String model, CatalogAttributes attributes) {
        if (attributes == null || !attributes.errors().isEmpty()) {
            throw badRequest("INVALID_ITEM", attributes == null ? "Catalog classification is required."
                    : attributes.errors().stream().map(issue -> issue.field() + ": " + issue.message())
                            .reduce((left, right) -> left + " " + right).orElse("Invalid classification."));
        }
        String cleanName = checked(name, 255);
        String cleanModel = checked(model, 255);
        if (cleanName == null) throw badRequest("INVALID_ITEM", "Item name is required.");
        // Preserve existing phone identities when broadening PHONE to GADGET/PHONE.
        String identityCategory = attributes.category() == ItemDomain.GADGET
                ? (attributes.gadgetType() == GadgetType.PHONE ? "PHONE" : "GADGET/" + attributes.gadgetType())
                : attributes.category().name();
        String identity = CatalogText.identity(identityCategory, attributes.brand(), cleanName, cleanModel,
                attributes.series(), attributes.applianceType(), attributes.size(), attributes.features());
        return items.findByIdentityKey(identity).orElseGet(() -> items.saveAndFlush(switch (attributes.category()) {
            case GADGET -> new Gadget(cleanName, attributes.brand(), cleanModel, attributes.series(), attributes.gadgetType(), identity);
            case ELECTRICAL_APPLIANCE -> new ElectricalAppliance(cleanName, attributes.brand(), cleanModel,
                    attributes.applianceType(), attributes.size(), attributes.features(), identity);
        }));
    }

    @Transactional(readOnly = true)
    public List<String> brands(ItemDomain domain) {
        return distinct(items.findAll(available(domain, null)).stream().map(Item::getBrand));
    }

    @Transactional(readOnly = true)
    public CatalogOptions options(ItemDomain domain, String brand) {
        String cleanBrand = requireBrand(brand);
        List<Item> available = items.findAll(available(domain, cleanBrand));
        return new CatalogOptions(domain, cleanBrand,
                distinct(available.stream().map(Item::getSeries)),
                distinct(available.stream().map(Item::getApplianceType)),
                distinct(available.stream().map(Item::getSize)),
                distinct(available.stream().map(Item::getFeatures).filter(value -> value != null)
                        .flatMap(value -> Arrays.stream(value.split("[,;]"))).map(CatalogText::text)),
                available.stream().map(Item::getGadgetType).filter(type -> type != null).distinct().sorted().toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> search(ItemDomain domain, CatalogQuery query, PageQuery page) {
        String brand = requireBrand(query.brand());
        String series = checked(query.series(), 255);
        String applianceType = checked(query.applianceType(), 255);
        String applianceSize = checked(query.applianceSize(), 255);
        String feature = checked(query.feature(), 1000);
        String search = checked(query.q(), 255);
        if (domain == ItemDomain.GADGET) {
            if (query.gadgetType() == null) throw badRequest("GADGET_TYPE_REQUIRED", "Choose a gadget type before requesting models.");
            if (query.gadgetType() == GadgetType.PHONE && series == null) throw badRequest("SERIES_REQUIRED", "Choose a phone series from the brand's options before requesting phones.");
            if (applianceType != null || applianceSize != null || feature != null) {
                throw badRequest("INVALID_DOMAIN_FILTER", "Appliance filters cannot be used in the gadget domain.");
            }
        } else if (series != null || query.gadgetType() != null) {
            throw badRequest("INVALID_DOMAIN_FILTER", "Gadget filters cannot be used in the electrical-appliance domain.");
        }
        Specification<Item> filters = (root, criteria, builder) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (domain == ItemDomain.GADGET) {
                var gadget = builder.treat(root, Gadget.class);
                conditions.add(builder.equal(gadget.get("gadgetType"), query.gadgetType()));
                if (series != null) conditions.add(builder.equal(builder.lower(gadget.get("series")), CatalogText.key(series)));
            } else {
                var appliance = builder.treat(root, ElectricalAppliance.class);
                if (applianceType != null) conditions.add(builder.equal(builder.lower(appliance.get("applianceType")), CatalogText.key(applianceType)));
                if (applianceSize != null) conditions.add(builder.equal(builder.lower(appliance.get("size")), CatalogText.key(applianceSize)));
                if (feature != null) conditions.add(builder.like(builder.lower(appliance.get("features")), pattern(feature), '\\'));
            }
            if (search != null) conditions.add(builder.or(
                    builder.like(builder.lower(root.get("name")), pattern(search), '\\'),
                    builder.like(builder.lower(root.get("model")), pattern(search), '\\')));
            return builder.and(conditions.toArray(Predicate[]::new));
        };
        return PageResponse.from(items.findAll(available(domain, brand).and(filters),
                PageRequest.of(page.page(), page.size(), Sort.by("name", "id"))).map(ItemResponse::from));
    }

    @Transactional(readOnly = true)
    public ItemResponse get(ItemDomain domain, UUID id) { return ItemResponse.from(requireItem(domain, id)); }

    @Transactional(readOnly = true)
    public PageResponse<OfferResponse> offers(ItemDomain domain, UUID id, String currency,
            String condition, PageQuery page) {
        requireItem(domain, id);
        String currencyCode = CurrencyCode.validate(currency);
        String itemCondition = checked(condition, 255);
        Specification<InventoryItem> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>(List.of(
                    builder.equal(root.get("item").get("id"), id), builder.isTrue(root.get("active")),
                    builder.isTrue(root.get("seller").get("user").get("enabled")),
                    builder.or(builder.isNull(root.get("quantity")), builder.greaterThan(root.get("quantity"), 0)),
                    builder.equal(root.get("currency"), currencyCode)));
            if (itemCondition != null) predicates.add(builder.equal(builder.lower(root.get("condition")), CatalogText.key(itemCondition)));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(inventory.findAll(specification,
                PageRequest.of(page.page(), page.size(), Sort.by("priceMinor", "id"))).map(OfferResponse::from));
    }

    private Item requireItem(ItemDomain domain, UUID id) {
        return items.findById(id).filter(item -> item.getCategory() == domain)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Item not found in the selected domain."));
    }

    private Specification<Item> available(ItemDomain domain, String brand) {
        return (root, query, builder) -> {
            var offers = query.subquery(Integer.class);
            var offer = offers.from(InventoryItem.class);
            offers.select(builder.literal(1)).where(builder.equal(offer.get("item"), root),
                    builder.isTrue(offer.get("active")), builder.isTrue(offer.get("seller").get("user").get("enabled")),
                    builder.or(builder.isNull(offer.get("quantity")), builder.greaterThan(offer.get("quantity"), 0)));
            Predicate category = builder.equal(root.type(), domain == ItemDomain.GADGET ? Gadget.class : ElectricalAppliance.class);
            return brand == null ? builder.and(category, builder.exists(offers))
                    : builder.and(category, builder.exists(offers), builder.equal(root.get("brandKey"), CatalogText.key(brand)));
        };
    }

    private static List<String> distinct(Stream<String> values) {
        TreeMap<String, String> unique = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        values.filter(value -> value != null && !value.isBlank()).forEach(value -> unique.putIfAbsent(value, value));
        return List.copyOf(unique.values());
    }

    private static String requireBrand(String brand) {
        String value = CatalogText.brand(checked(brand, 255));
        if (value == null) throw badRequest("BRAND_REQUIRED", "Choose a brand within the selected domain first.");
        return value;
    }

    private static String checked(String value, int max) {
        String text = CatalogText.text(value);
        if (text != null && (text.length() > max || text.indexOf('\0') >= 0)) {
            throw badRequest("INVALID_PARAMETERS", "Catalog text is too long or contains NUL characters.");
        }
        return text;
    }

    private static String pattern(String value) {
        return "%" + CatalogText.key(value).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }
}
