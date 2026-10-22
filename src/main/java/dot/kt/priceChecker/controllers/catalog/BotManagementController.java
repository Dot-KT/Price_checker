package dot.kt.priceChecker.controllers.catalog;

import dot.kt.priceChecker.dto.catalog.ItemResponse;
import dot.kt.priceChecker.dto.common.*;
import dot.kt.priceChecker.dto.intel.*;
import dot.kt.priceChecker.entities.catalog.Item;
import dot.kt.priceChecker.repo.catalog.ItemRepository;
import dot.kt.priceChecker.service.intel.PriceIntelService;
import dot.kt.priceChecker.service.telegram.TelegramAdminService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management")
public class BotManagementController {
    private final PriceIntelService prices;
    private final TelegramAdminService telegram;
    private final ItemRepository items;
    public BotManagementController(PriceIntelService prices, TelegramAdminService telegram, ItemRepository items) {
        this.prices = prices; this.telegram = telegram; this.items = items;
    }
    @GetMapping("/items")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('project:read')")
    public PageResponse<ItemResponse> items(@RequestParam(defaultValue = "") String q, @Valid @ModelAttribute PageQuery page) {
        if (q.length() > 255 || q.indexOf('\0') >= 0) throw new dot.kt.priceChecker.exceptions.common.ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETERS", "Invalid search text.");
        String pattern = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<Item> filter = (root, query, builder) -> builder.or(builder.like(builder.lower(root.get("name")), pattern, '\\'),
                builder.like(builder.lower(root.get("brand")), pattern, '\\'), builder.like(builder.lower(root.get("model")), pattern, '\\'));
        return PageResponse.from(items.findAll(filter, PageRequest.of(page.page(), page.size(), Sort.by("name", "id"))).map(ItemResponse::from));
    }
    @GetMapping("/items/{id}/price-intel")
    public PriceQuote price(@PathVariable UUID id, @RequestParam(defaultValue = "NGN") String currency) { return prices.quote(id, currency, null); }
    @PutMapping("/items/{id}/price-intel")
    public PriceQuote save(@PathVariable UUID id, @Valid @RequestBody SavePriceIntelRequest request) { return prices.save(id, request); }
    @DeleteMapping("/items/{id}/price-intel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam(defaultValue = "NGN") String currency) { prices.delete(id, currency); }
    @GetMapping("/telegram/users")
    public PageResponse<TelegramAdminService.Profile> profiles(@RequestParam(required = false) String q, @Valid @ModelAttribute PageQuery page) { return telegram.profiles(q, page); }
    @GetMapping("/telegram/users/{telegramId}/conversations")
    public PageResponse<TelegramAdminService.Conversation> history(@PathVariable long telegramId, @Valid @ModelAttribute PageQuery page) { return telegram.history(telegramId, page); }
}
