package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.dto.catalog.ItemResponse;
import dot.kt.priceChecker.dto.telegram.*;
import dot.kt.priceChecker.service.intel.PriceIntelService;
import dot.kt.priceChecker.utils.catalog.MoneyText;
import dot.kt.priceChecker.utils.common.CurrencyCode;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BotInformationService {
    public static final String HANDOFF_URL = "https://wa.link/nij6bk";
    private final ProductAssistant assistant;
    private final BotCatalogService catalog;
    private final PriceIntelService prices;
    public BotInformationService(ProductAssistant assistant, BotCatalogService catalog, PriceIntelService prices) {
        this.assistant = assistant; this.catalog = catalog; this.prices = prices;
    }

    public AssistantResult answer(AssistantWork work) {
        QuestionIntent intent;
        try { intent = work.forcedItemId() == null ? assistant.identify(work) : work.previousIntent(); }
        catch (RuntimeException exception) {
            return new AssistantResult(null, "I couldn't reach Ollama just now. Please try again, or ask for help at " + HANDOFF_URL,
                    List.of(), List.of(), false);
        }
        if (intent == null) return new AssistantResult(null, "Please describe the exact item and what you want to know.", List.of(), List.of(), false);
        if (intent.wrongDomain()) return new AssistantResult(intent, "That request is outside your selected category. Send cancel, then choose the right category.", List.of(), List.of(), false);
        List<String> names = new ArrayList<>(intent.items() == null ? List.of() : intent.items().stream().filter(Objects::nonNull).limit(2).toList());
        List<ItemResponse> matched = new ArrayList<>();
        List<ItemResponse> resolved = new ArrayList<>();
        if (work.forcedItemId() != null) {
            var selected = catalog.item(work.forcedItemId(), work.domain(), work.gadgetType());
            if (selected.isEmpty()) return new AssistantResult(intent, "That item is no longer available. Please describe another item.", List.of(), List.of(), false);
            int index = names.indexOf(intent.clarification());
            if (index < 0) index = 0;
            if (names.isEmpty()) names.add(BotCatalogService.displayName(selected.get())); else names.set(index, BotCatalogService.displayName(selected.get()));
            resolved.addAll(catalog.listedItems(work.resolvedItemIds())); resolved.add(selected.get());
        }
        if (names.isEmpty()) return new AssistantResult(intent,
                intent.clarification() == null ? "Which exact model do you mean?" : intent.clarification(), List.of(), List.of(), false);
        for (String name : names) {
            var previous = resolved.stream().filter(item -> BotCatalogService.displayName(item).equals(name)
                    && item.category() == work.domain() && (work.domain() != dot.kt.priceChecker.enums.catalog.ItemDomain.GADGET || item.gadgetType() == work.gadgetType())).findFirst();
            if (previous.isPresent()) { matched.add(previous.get()); continue; }
            var match = catalog.match(work.domain(), work.gadgetType(), name);
            if (match.exact() != null) matched.add(match.exact());
            else if (!match.choices().isEmpty()) {
                QuestionIntent choice = new QuestionIntent(names, intent.price(), intent.details(), intent.comparison(), intent.currency(), intent.condition(), name, false);
                return new AssistantResult(choice, "Which exact model do you mean by " + name + "?",
                        matched.stream().map(ItemResponse::id).toList(), match.choices().stream().map(ItemResponse::id).toList(), true);
            }
        }
        String currency;
        try { currency = CurrencyCode.validate(intent.currency() == null ? "NGN" : intent.currency()); }
        catch (RuntimeException exception) { currency = "NGN"; }
        intent = new QuestionIntent(names, intent.price(), intent.details(), intent.comparison(), currency,
                intent.condition(), null, false);
        StringBuilder answer = new StringBuilder();
        StringBuilder html = new StringBuilder();
        try {
            SpecificationReport report = assistant.explain(work, names);
            answer.append("Specifications:\n").append(report.text()).append("\n\n");
            html.append("<h3>Specifications</h3>").append(report.richHtml() == null ? "<p>" + SpecificationTable.escape(report.text()) + "</p>" : report.richHtml());
        } catch (RuntimeException exception) {
            var unavailable = SpecificationTable.render(names, Map.of("Status", names.stream().map(name -> "Not verified: live lookup unavailable").toList()));
            answer.append(unavailable.text()).append("\n\n"); html.append(unavailable.richHtml());
        }
        if (intent.price()) {
            List<String> quoteNames = new ArrayList<>(), amounts = new ArrayList<>(), sources = new ArrayList<>(), counts = new ArrayList<>();
            for (ItemResponse item : matched) {
                var quote = prices.quote(item.id(), currency, intent.condition());
                quoteNames.add(BotCatalogService.displayName(item));
                amounts.add(quote.priceMinor() == null ? "No available price in " + currency : MoneyText.format(quote.priceMinor(), currency));
                sources.add(quote.source()); counts.add(quote.offerCount() == 0 ? "No seller average used" : quote.offerCount() + " offers from " + quote.sellerCount() + " sellers");
            }
            if (!quoteNames.isEmpty()) {
                Map<String, List<String>> rows = new LinkedHashMap<>(); rows.put("Price", amounts); rows.put("Price source", sources); rows.put("Eligible offers", counts);
                var table = SpecificationTable.render(quoteNames, rows);
                answer.append("Database prices:\n").append(table.text()).append('\n'); html.append("<h3>Database prices</h3>").append(table.richHtml());
            }
        }
        if (matched.size() < names.size()) {
            String notice = "One or more requested items aren't in our database. Ask further questions at " + HANDOFF_URL;
            answer.append('\n').append(notice).append('\n'); html.append("<p>").append(SpecificationTable.escape(notice)).append("</p>");
        }
        if (!matched.isEmpty()) {
            String notice = "Would you like to check available sellers and their exact prices?";
            answer.append('\n').append(notice); html.append("<p>").append(notice).append("</p>");
        }
        return new AssistantResult(intent, answer.toString().strip(), matched.stream().map(ItemResponse::id).distinct().toList(), List.of(), false, html.toString());
    }
}
