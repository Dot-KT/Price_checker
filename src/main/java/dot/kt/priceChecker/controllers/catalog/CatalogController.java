package dot.kt.priceChecker.controllers.catalog;

import dot.kt.priceChecker.dto.catalog.CatalogOptions;
import dot.kt.priceChecker.dto.catalog.CatalogQuery;
import dot.kt.priceChecker.dto.catalog.ItemResponse;
import dot.kt.priceChecker.dto.catalog.OfferResponse;
import dot.kt.priceChecker.dto.common.PageQuery;
import dot.kt.priceChecker.dto.common.PageResponse;
import dot.kt.priceChecker.enums.catalog.ItemDomain;
import dot.kt.priceChecker.service.catalog.CatalogService;
import dot.kt.priceChecker.service.intel.PriceIntelService;
import dot.kt.priceChecker.dto.intel.PriceQuote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.security.SecurityRequirements
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog", description = "Choose a domain, then a brand and domain-specific options before requesting models and seller prices")
public class CatalogController {
    private final CatalogService catalog;
    private final PriceIntelService prices;

    public CatalogController(CatalogService catalog, PriceIntelService prices) { this.catalog = catalog; this.prices = prices; }

    @GetMapping("/{domain}/items/{id}/price")
    public PriceQuote price(@PathVariable ItemDomain domain, @PathVariable UUID id,
            @RequestParam(defaultValue = "NGN") String currency, @RequestParam(required = false) String condition) {
        catalog.get(domain, id);
        PriceQuote value = prices.quote(id, currency, condition);
        return new PriceQuote(value.itemId(), value.itemName(), value.currency(), value.priceMinor(), value.source(),
                value.offerCount(), value.sellerCount(), null, value.updatedAt());
    }

    public record DomainOption(ItemDomain domain, String label) { }

    @GetMapping("/domains")
    @Operation(summary = "Choose an item domain")
    public List<DomainOption> domains() {
        return Arrays.stream(ItemDomain.values()).map(domain -> new DomainOption(domain, domain.label())).toList();
    }

    @GetMapping("/{domain}/brands")
    @Operation(summary = "Suggest available brands in the selected domain")
    public List<String> brands(@PathVariable ItemDomain domain) { return catalog.brands(domain); }

    @GetMapping("/{domain}/options")
    @Operation(summary = "Suggest gadget types and phone series, or appliance types, sizes, and features for a brand")
    public CatalogOptions options(@PathVariable ItemDomain domain, @RequestParam String brand) {
        return catalog.options(domain, brand);
    }

    @GetMapping("/{domain}/items")
    @Operation(summary = "Suggest available exact models", description = "A brand is required. Gadgets require gadgetType; PHONE gadgets also require series. Appliance filters support applianceType, applianceSize, and feature. q searches item names and model codes; size is the page size.")
    public PageResponse<ItemResponse> items(@PathVariable ItemDomain domain,
            @Valid @ParameterObject @ModelAttribute CatalogQuery query,
            @Valid @ParameterObject @ModelAttribute PageQuery page) {
        return catalog.search(domain, query, page);
    }

    @GetMapping("/{domain}/items/{id}")
    @Operation(summary = "Read an item within the selected domain")
    public ItemResponse item(@PathVariable ItemDomain domain, @PathVariable UUID id) {
        return catalog.get(domain, id);
    }

    @GetMapping("/{domain}/items/{id}/offers")
    @Operation(summary = "Compare seller prices for an exact item", description = "Returns active, in-stock or unknown-stock offers from enabled sellers, sorted by price within one currency. currency defaults to NGN; condition is an optional exact, case-insensitive filter.")
    public PageResponse<OfferResponse> offers(@PathVariable ItemDomain domain, @PathVariable UUID id,
            @RequestParam(defaultValue = "NGN") String currency, @RequestParam(required = false) String condition,
            @Valid @ParameterObject @ModelAttribute PageQuery page) {
        return catalog.offers(domain, id, currency, condition, page);
    }
}
