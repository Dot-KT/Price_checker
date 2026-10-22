package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.dto.catalog.ItemResponse;
import dot.kt.priceChecker.dto.telegram.BotOffer;
import dot.kt.priceChecker.entities.catalog.*;
import dot.kt.priceChecker.enums.catalog.*;
import dot.kt.priceChecker.repo.catalog.ItemRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.service.intel.PriceIntelService;
import dot.kt.priceChecker.utils.catalog.CatalogText;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BotCatalogService {
    private final ItemRepository items;
    private final InventoryItemRepository inventory;
    public BotCatalogService(ItemRepository items, InventoryItemRepository inventory) { this.items = items; this.inventory = inventory; }
    public record Match(ItemResponse exact, List<ItemResponse> choices) { }

    @Transactional(readOnly = true)
    public Match match(ItemDomain domain, GadgetType type, String name) {
        String needle = key(name);
        Specification<Item> scope = (root, query, builder) -> {
            var category = builder.equal(root.type(), domain == ItemDomain.GADGET ? Gadget.class : ElectricalAppliance.class);
            return domain == ItemDomain.GADGET && type != null ? builder.and(category,
                    builder.equal(builder.treat(root, Gadget.class).get("gadgetType"), type)) : category;
        };
        List<ItemResponse> candidates = items.findAll(scope).stream().map(ItemResponse::from).toList();
        List<ItemResponse> exact = candidates.stream().filter(item -> aliases(item).contains(needle)).toList();
        if (exact.size() == 1) return new Match(exact.getFirst(), List.of());
        if (exact.size() > 1) return new Match(null, exact.stream().limit(12).toList());
        List<ItemResponse> choices = candidates.stream().filter(item -> aliases(item).stream()
                .anyMatch(alias -> !needle.isBlank() && alias.contains(needle)) || tokenMatch(item, name))
                .sorted(Comparator.comparing(ItemResponse::name)).limit(12).toList();
        return new Match(null, choices);
    }

    @Transactional(readOnly = true)
    public Optional<ItemResponse> item(UUID id, ItemDomain domain, GadgetType type) {
        return items.findById(id).filter(item -> item.getCategory() == domain
                && (domain != ItemDomain.GADGET || item.getGadgetType() == type)).map(ItemResponse::from);
    }

    @Transactional(readOnly = true)
    public List<BotOffer> offers(List<UUID> itemIds, String currency, String condition) {
        List<BotOffer> result = new ArrayList<>();
        for (UUID itemId : itemIds) {
            inventory.findAll(PriceIntelService.eligible(itemId, currency, condition), Sort.by("priceMinor", "id"))
                    .forEach(row -> result.add(new BotOffer(row.getId(), itemId, row.getItem().getName(), row.getSeller().getDisplayName(),
                            row.getModel(), row.getCondition(), row.getDescription(), row.getPriceMinor(), row.getCurrency(), row.getQuantity())));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> listedItems(List<UUID> ids) { return items.findAllById(ids).stream().map(ItemResponse::from).toList(); }

    private static Set<String> aliases(ItemResponse item) {
        Set<String> result = new HashSet<>();
        result.add(key(item.name())); result.add(key(item.brand() + " " + item.name()));
        if (item.model() != null) { result.add(key(item.model())); result.add(key(item.brand() + " " + item.model())); }
        return result;
    }
    public static String displayName(ItemResponse item) {
        String name = item.name();
        if (item.model() != null && !key(name).contains(key(item.model()))) name += " [" + item.model() + "]";
        if (item.size() != null && !key(name).contains(key(item.size()))) name += " [" + item.size() + "]";
        return name;
    }
    private static boolean tokenMatch(ItemResponse item, String name) {
        String combined = key(item.brand() + " " + item.name() + " " + Objects.toString(item.model(), "") + " " + Objects.toString(item.size(), ""));
        List<String> tokens = Arrays.stream(CatalogText.key(name).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isBlank()).toList();
        return !tokens.isEmpty() && tokens.stream().allMatch(combined::contains);
    }
    private static String key(String value) { return CatalogText.key(value).replaceAll("[^\\p{L}\\p{N}]", ""); }
}
