package dot.kt.priceChecker.service.intel;

import dot.kt.priceChecker.dto.intel.*;
import dot.kt.priceChecker.entities.intel.PriceCheckerIntel;
import dot.kt.priceChecker.entities.inventory.InventoryItem;
import dot.kt.priceChecker.exceptions.common.ApiException;
import dot.kt.priceChecker.repo.catalog.ItemRepository;
import dot.kt.priceChecker.repo.intel.PriceIntelRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.repo.users.UserRepository;
import dot.kt.priceChecker.service.security.CurrentUserService;
import dot.kt.priceChecker.utils.common.CurrencyCode;
import dot.kt.priceChecker.utils.catalog.CatalogText;
import java.math.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceIntelService {
    private final PriceIntelRepository intel;
    private final ItemRepository items;
    private final InventoryItemRepository inventory;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final Clock clock;
    public PriceIntelService(PriceIntelRepository intel, ItemRepository items, InventoryItemRepository inventory,
            UserRepository users, CurrentUserService currentUser, Clock clock) {
        this.intel = intel; this.items = items; this.inventory = inventory; this.users = users; this.currentUser = currentUser; this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAuthority('project:write')")
    public PriceQuote save(UUID itemId, SavePriceIntelRequest request) {
        var item = items.findById(itemId).orElseThrow(() -> missing());
        String currency = CurrencyCode.validate(request.currency());
        long minor;
        try {
            minor = request.price().setScale(Currency.getInstance(currency).getDefaultFractionDigits(), RoundingMode.UNNECESSARY)
                    .movePointRight(Currency.getInstance(currency).getDefaultFractionDigits()).longValueExact();
            if (minor < 0) throw new ArithmeticException();
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "Price has too many decimal places or exceeds the supported range.");
        }
        if (request.notes() != null && request.notes().indexOf('\0') >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETERS", "Notes must not contain NUL characters.");
        }
        var value = intel.findByItemIdAndCurrency(itemId, currency).orElseGet(() -> new PriceCheckerIntel(item, currency));
        value.update(minor, request.notes(), users.findById(currentUser.id()).orElseThrow(), clock.instant());
        intel.saveAndFlush(value);
        return quote(itemId, currency, null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('project:write')")
    public void delete(UUID itemId, String currency) { intel.deleteByItemIdAndCurrency(itemId, CurrencyCode.validate(currency)); }

    @Transactional(readOnly = true)
    public PriceQuote quote(UUID itemId, String currency, String condition) {
        var item = items.findById(itemId).orElseThrow(() -> missing());
        String code = CurrencyCode.validate(currency);
        var specified = intel.findByItemIdAndCurrency(itemId, code);
        if (specified.isPresent()) {
            var price = specified.get();
            return new PriceQuote(itemId, item.getName(), code, price.getPriceMinor(), "Price checker specified intel", 0, 0, price.getNotes(), price.getUpdatedAt());
        }
        List<InventoryItem> offers = inventory.findAll(eligible(itemId, code, condition));
        if (offers.isEmpty()) return new PriceQuote(itemId, item.getName(), code, null, "Unavailable", 0, 0, null, null);
        BigDecimal total = offers.stream().map(row -> BigDecimal.valueOf(row.getPriceMinor())).reduce(BigDecimal.ZERO, BigDecimal::add);
        long average = total.divide(BigDecimal.valueOf(offers.size()), 0, RoundingMode.HALF_UP).longValueExact();
        int sellerCount = (int) offers.stream().map(row -> row.getSeller().getId()).distinct().count();
        return new PriceQuote(itemId, item.getName(), code, average, "Seller average", offers.size(), sellerCount, null,
                offers.stream().map(InventoryItem::getUpdatedAt).max(Comparator.naturalOrder()).orElse(null));
    }

    public static Specification<InventoryItem> eligible(UUID itemId, String currency, String condition) {
        return (root, query, builder) -> {
            var predicate = builder.and(builder.equal(root.get("item").get("id"), itemId), builder.isTrue(root.get("active")),
                    builder.isTrue(root.get("seller").get("user").get("enabled")),
                    builder.or(builder.isNull(root.get("quantity")), builder.greaterThan(root.get("quantity"), 0)),
                    builder.equal(root.get("currency"), currency));
            return condition == null || condition.isBlank() ? predicate : builder.and(predicate,
                    builder.equal(builder.lower(root.get("condition")), CatalogText.key(condition)));
        };
    }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Catalog item not found."); }
}
