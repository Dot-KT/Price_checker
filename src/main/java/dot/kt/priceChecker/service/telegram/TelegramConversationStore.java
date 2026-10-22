package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.config.telegram.TelegramProperties;
import dot.kt.priceChecker.dto.telegram.*;
import dot.kt.priceChecker.entities.telegram.*;
import dot.kt.priceChecker.enums.catalog.*;
import dot.kt.priceChecker.enums.telegram.ConversationPhase;
import dot.kt.priceChecker.repo.telegram.*;
import dot.kt.priceChecker.repo.catalog.ItemRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.utils.catalog.MoneyText;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TelegramConversationStore {
    private final TelegramUserRepository users;
    private final TelegramConversationRepository conversations;
    private final TelegramUpdateRepository updates;
    private final ItemRepository items;
    private final InventoryItemRepository inventory;
    private final BotCatalogService catalog;
    private final TelegramProperties properties;
    private final JsonMapper json;
    private final Clock clock;
    public TelegramConversationStore(TelegramUserRepository users, TelegramConversationRepository conversations,
            TelegramUpdateRepository updates, ItemRepository items, InventoryItemRepository inventory,
            BotCatalogService catalog, TelegramProperties properties, JsonMapper json, Clock clock) {
        this.users = users; this.conversations = conversations; this.updates = updates; this.items = items;
        this.inventory = inventory; this.catalog = catalog; this.properties = properties; this.json = json; this.clock = clock;
    }
    @Transactional
    public void receive(TelegramIncoming input) {
        if (!updates.existsById(input.updateId())) updates.save(new TelegramUpdateReceipt(input.updateId(),
                input.telegramId(), input.chatId(), encode(input), clock.instant()));
    }
    @Transactional(readOnly = true)
    public long offset() {
        Instant last = updates.lastReceivedAt();
        // Telegram may choose a random update ID after a week without new updates.
        return last == null || last.isBefore(clock.instant().minus(Duration.ofDays(6))) ? 0 : updates.latestUpdateId() + 1;
    }
    public record Pending(long updateId, long chatId) { }
    @Transactional(readOnly = true)
    public List<Pending> pending() {
        return updates.pending(clock.instant(), PageRequest.of(0, 100)).stream()
                .map(row -> new Pending(row.getUpdateId(), row.getChatId())).toList();
    }
    @Transactional
    public boolean claim(long id) {
        var row = updates.lock(id).orElseThrow();
        if (row.getLeaseUntil() != null && row.getLeaseUntil().isAfter(clock.instant())) return false;
        row.lease(clock.instant().plusSeconds(600)); return true;
    }
    @Transactional
    public void retryLater(long id, int seconds) { updates.lock(id).orElseThrow().lease(clock.instant().plusSeconds(seconds)); }
    @Transactional
    public AssistantWork prepare(long id) {
        var receipt = updates.lock(id).orElseThrow();
        if (receipt.getProcessedAt() != null) return null;
        var input = json.readValue(receipt.getIncomingJson(), TelegramIncoming.class);
        if (!input.privateChat()) { finish(receipt, List.of()); return null; }
        Instant now = clock.instant();
        var user = users.lockByTelegramId(input.telegramId()).orElseGet(() -> users.save(new TelegramUser(input.telegramId(), now)));
        user.updateProfile(input.username(), input.firstName(), input.lastName(), now);
        var active = conversations.active(user.getId());
        TelegramConversation conversation = active.isEmpty() ? null : active.getFirst();
        if (conversation != null && !conversation.getLastActivityAt().plusSeconds(properties.getConversationTimeoutSeconds()).isAfter(now)) {
            conversation.end(ConversationPhase.EXPIRED, now); conversation = null;
        }
        String text = input.text() == null ? "" : input.text().replace("\0", "").strip();
        if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("/cancel")) {
            if (conversation != null) conversation.end(ConversationPhase.CANCELLED, now);
            finish(receipt, List.of(BotReply.text("Conversation cancelled. Send any message when you want to start again."))); return null;
        }
        if (text.equalsIgnoreCase("/start") && conversation != null) {
            conversation.end(ConversationPhase.CANCELLED, now); conversation = null;
        }
        if (conversation == null) {
            conversation = conversations.save(new TelegramConversation(user, now));
            user.select(null, null, null, now);
            finish(receipt, List.of(BotReply.text("Hi! I'm Ch3cker. I help with product details, comparisons and prices. What's your original name? You can send cancel at any time."))); return null;
        }
        String action = null;
        if (input.callbackData() != null) {
            String prefix = "c:" + conversation.getId() + ":" + conversation.getKeyboardVersion() + ":";
            if (!input.callbackData().startsWith(prefix)) {
                finish(receipt, List.of(BotReply.text("That button belongs to an earlier conversation or an older step. Please use the latest buttons, or send cancel to start again."))); return null;
            }
            action = input.callbackData().substring(prefix.length());
        }
        conversation.touch(now);
        List<BotReply> replies = new ArrayList<>();
        switch (conversation.getPhase()) {
            case NAME -> {
                if (action != null || text.isBlank() || text.length() > 255 || text.startsWith("/")) replies.add(BotReply.text("Please send your name (up to 255 characters)."));
                else {
                    user.name(text, now); conversation.phase(ConversationPhase.DOMAIN); conversation.nextButtons();
                    replies.add(new BotReply("Thanks, " + user.getRealName() + ". What are you interested in?", List.of(List.of(
                            button(conversation, "Gadgets", "d:G"), button(conversation, "Appliances", "d:A")))));
                }
            }
            case DOMAIN -> {
                if ("d:G".equals(action) || text.equalsIgnoreCase("gadgets")) {
                    conversation.chooseDomain(ItemDomain.GADGET); conversation.nextButtons();
                    List<List<BotReply.Button>> buttons = new ArrayList<>();
                    for (GadgetType type : GadgetType.values()) buttons.add(List.of(button(conversation, type.name().replace('_', ' '), "g:" + type.name())));
                    replies.add(new BotReply("Which kind of gadget?", buttons));
                } else if ("d:A".equals(action) || text.equalsIgnoreCase("appliances")) {
                    conversation.chooseDomain(ItemDomain.ELECTRICAL_APPLIANCE); conversation.nextButtons(); replies.add(requestPrompt());
                } else replies.add(BotReply.text("Please choose Gadgets or Appliances using the buttons above."));
            }
            case GADGET_TYPE -> {
                GadgetType type = null;
                try { type = GadgetType.fromSheet(action != null && action.startsWith("g:") ? action.substring(2) : text); }
                catch (RuntimeException ignored) { }
                if (type == null) replies.add(BotReply.text("Please choose a gadget type using the buttons above."));
                else { conversation.chooseGadgetType(type); conversation.nextButtons(); replies.add(requestPrompt()); }
            }
            case CHOOSING_ITEM -> {
                UUID forced = index(action, "i:", ids(conversation.getCandidateIdsJson()));
                if (action == null && !text.isBlank() && text.length() <= 4096) return work(conversation, id, text, null);
                else if (forced == null) replies.add(BotReply.text("Please choose an exact model using the latest buttons, or describe it more precisely."));
                else return work(conversation, id, conversation.getQuestion(), forced);
            }
            case CHOOSING_OFFER -> {
                if (action == null && yes(text)) { replies.addAll(offerPage(conversation, 0)); break; }
                if (action == null && !text.isBlank() && !yes(text) && text.length() <= 4096) return work(conversation, id, text, null);
                if (action != null && action.startsWith("p:")) {
                    int page;
                    try { page = Integer.parseInt(action.substring(2)); } catch (NumberFormatException ignored) { page = -1; }
                    replies.addAll(offerPage(conversation, page));
                } else {
                    UUID chosen = index(action, "o:", ids(conversation.getOfferIdsJson()));
                    var listing = chosen == null ? null : inventory.findById(chosen).orElse(null);
                    var intent = intent(conversation);
                    List<BotOffer> available = catalog.offers(ids(conversation.getItemIdsJson()), intent.currency(), intent.condition());
                    BotOffer offer = available.stream().filter(value -> value.id().equals(chosen)).findFirst().orElse(null);
                    List<BotOffer> snapshots = json.readValue(conversation.getOfferSnapshotsJson(), json.getTypeFactory().constructCollectionType(List.class, BotOffer.class));
                    BotOffer displayed = snapshots.stream().filter(value -> value.id().equals(chosen)).findFirst().orElse(null);
                    if (listing == null || offer == null) replies.add(BotReply.text("That offer is no longer available. Send yes to refresh seller offers, or cancel to start again."));
                    else if (!offer.equals(displayed)) replies.add(BotReply.text("That offer has changed since it was displayed. Send yes to refresh seller offers and review the latest details before selecting."));
                    else {
                        String snapshot = encode(offer); conversation.select(snapshot);
                        user.select(items.findById(offer.itemId()).orElseThrow(), listing, snapshot, now);
                        conversation.end(ConversationPhase.COMPLETED, now);
                        replies.add(new BotReply("Saved your selection: " + offer.itemName() + " from " + offer.sellerName() + ". Continue with our team and tell them your name, " + user.getRealName() + ", so they can find your selection.",
                                List.of(List.of(BotReply.Button.link("Continue with our team", BotInformationService.HANDOFF_URL)))));
                    }
                }
            }
            default -> {
                if (("y".equals(action) || yes(text)) && !ids(conversation.getItemIdsJson()).isEmpty()) replies.addAll(offerPage(conversation, 0));
                else if ("n".equals(action) || text.equalsIgnoreCase("no")) {
                    conversation.phase(ConversationPhase.REQUEST); conversation.nextButtons(); replies.add(BotReply.text("What else would you like to know about these items?"));
                } else if (action != null || text.isBlank()) replies.add(requestPrompt());
                else if (text.length() > 4096) replies.add(BotReply.text("Please keep your question within 4096 characters."));
                else return work(conversation, id, text, null);
            }
        }
        finish(receipt, replies); return null;
    }
    private AssistantWork work(TelegramConversation conversation, long updateId, String question, UUID forced) {
        QuestionIntent previous = intent(conversation);
        conversation.question(question, updateId);
        conversation.nextButtons();
        return new AssistantWork(updateId, conversation.getId(), conversation.getDomain(), conversation.getGadgetType(), question,
                previous == null || previous.items() == null ? List.of() : previous.items(), forced, previous, ids(conversation.getItemIdsJson()));
    }
    @Transactional
    public void complete(AssistantWork work, AssistantResult result) {
        var receipt = updates.lock(work.updateId()).orElseThrow();
        if (receipt.getProcessedAt() != null) return;
        var conversation = conversations.findById(work.conversationId()).orElseThrow();
        if (conversation.getEndedAt() != null || !Objects.equals(conversation.getPendingUpdateId(), work.updateId())
                || !conversation.getLastActivityAt().plusSeconds(properties.getConversationTimeoutSeconds()).isAfter(clock.instant())) {
            if (conversation.getEndedAt() == null) conversation.end(ConversationPhase.EXPIRED, clock.instant());
            finish(receipt, List.of(BotReply.text("This conversation has ended. Send any message to start again."))); return;
        }
        conversation.answered();
        conversation.nextButtons();
        conversation.context(result.intent() == null ? conversation.getIntentJson() : encode(result.intent()), encode(result.itemIds()), encode(result.candidateIds()));
        List<List<BotReply.Button>> buttons = new ArrayList<>();
        if (result.needsChoice()) {
            conversation.phase(ConversationPhase.CHOOSING_ITEM);
            var candidates = catalog.listedItems(result.candidateIds());
            for (int i = 0; i < result.candidateIds().size(); i++) {
                var item = items.findById(result.candidateIds().get(i)).orElseThrow();
                boolean repeated = candidates.stream().filter(other -> other.name().equals(item.getName())).count() > 1;
                String label = BotCatalogService.displayName(dot.kt.priceChecker.dto.catalog.ItemResponse.from(item));
                if (repeated) label += " | " + item.getBrand() + " | " + value(item.getModel()) + " | "
                        + (item.getCategory() == ItemDomain.GADGET ? value(item.getSeries()) : value(item.getSize()));
                buttons.add(List.of(button(conversation, label, "i:" + i)));
            }
        } else {
            conversation.phase(result.itemIds().isEmpty() ? ConversationPhase.REQUEST : ConversationPhase.OFFER_PROMPT);
            if (!result.itemIds().isEmpty()) buttons.add(List.of(button(conversation, "Show sellers", "y"), button(conversation, "Ask more", "n")));
        }
        finish(receipt, List.of(new BotReply(result.text(), buttons, result.richHtml())));
    }
    private List<BotReply> offerPage(TelegramConversation conversation, int page) {
        QuestionIntent intent = intent(conversation);
        List<BotOffer> offers = catalog.offers(ids(conversation.getItemIdsJson()), intent.currency(), intent.condition());
        // Save one stable list per displayed set; subsequent pages resolve only these server-side IDs.
        List<UUID> saved = ids(conversation.getOfferIdsJson());
        if (page == 0) { saved = offers.stream().map(BotOffer::id).toList(); conversation.offerIds(encode(saved)); }
        if (saved.isEmpty()) { conversation.phase(ConversationPhase.REQUEST); return List.of(BotReply.text("There are no available seller offers for this currency and condition. Please contact " + BotInformationService.HANDOFF_URL)); }
        if (page < 0 || page > (saved.size() - 1) / 8) return List.of(BotReply.text("Please use a current page button."));
        conversation.nextButtons();
        conversation.phase(ConversationPhase.CHOOSING_OFFER);
        Map<UUID, BotOffer> displayed = new LinkedHashMap<>();
        List<BotOffer> snapshots = json.readValue(conversation.getOfferSnapshotsJson(), json.getTypeFactory().constructCollectionType(List.class, BotOffer.class));
        if (page != 0) snapshots.forEach(offer -> displayed.put(offer.id(), offer));
        List<BotReply> replies = new ArrayList<>();
        for (int i = page * 8; i < Math.min(saved.size(), page * 8 + 8); i++) {
            UUID id = saved.get(i);
            BotOffer offer = offers.stream().filter(row -> row.id().equals(id)).findFirst().orElse(null);
            if (offer == null) continue;
            displayed.put(offer.id(), offer);
            Map<String, List<String>> detailRows = new LinkedHashMap<>();
            detailRows.put("Seller", List.of(offer.sellerName())); detailRows.put("Model", List.of(value(offer.model())));
            detailRows.put("Condition", List.of(value(offer.condition()))); detailRows.put("Description", List.of(value(offer.description())));
            detailRows.put("Price", List.of(MoneyText.format(offer.priceMinor(), offer.currency()))); detailRows.put("Quantity", List.of(value(offer.quantity())));
            var details = SpecificationTable.render(List.of(offer.itemName()), detailRows, 4000);
            replies.add(new BotReply(details.text(), List.of(List.of(button(conversation, "Select this offer", "o:" + i))), details.richHtml()));
        }
        List<BotReply.Button> navigation = new ArrayList<>();
        if (page > 0) navigation.add(button(conversation, "Previous", "p:" + (page - 1)));
        if ((page + 1) * 8 < saved.size()) navigation.add(button(conversation, "Next", "p:" + (page + 1)));
        replies.add(new BotReply("Choose a seller offer. Page " + (page + 1) + " of " + ((saved.size() + 7) / 8) + ".", navigation.isEmpty() ? List.of() : List.of(navigation)));
        conversation.offerSnapshots(encode(displayed.values()));
        return replies;
    }
    private static String value(Object value) { return value == null ? "Not specified" : value.toString(); }
    private static BotReply requestPrompt() { return BotReply.text("Describe the exact item and what you want to know: price, specifications, or a comparison of two items."); }
    private static boolean yes(String text) { return text.equalsIgnoreCase("yes") || text.equalsIgnoreCase("show sellers"); }
    private static BotReply.Button button(TelegramConversation c, String text, String action) { return BotReply.Button.action(text, "c:" + c.getId() + ":" + c.getKeyboardVersion() + ":" + action); }
    private static UUID index(String action, String prefix, List<UUID> values) {
        if (action == null || !action.startsWith(prefix)) return null;
        try { int i = Integer.parseInt(action.substring(prefix.length())); return i < 0 || i >= values.size() ? null : values.get(i); }
        catch (NumberFormatException ignored) { return null; }
    }
    private String encode(Object value) { return json.writeValueAsString(value); }
    private List<UUID> ids(String text) { return json.readValue(text, json.getTypeFactory().constructCollectionType(List.class, UUID.class)); }
    private QuestionIntent intent(TelegramConversation conversation) { return conversation.getIntentJson() == null ? null : json.readValue(conversation.getIntentJson(), QuestionIntent.class); }
    private void finish(TelegramUpdateReceipt receipt, List<BotReply> replies) { receipt.complete(encode(BotReply.split(replies)), clock.instant()); }
    public record Delivery(long chatId, String callbackId, List<BotReply> replies, int delivered) { }
    @Transactional(readOnly = true)
    public Delivery delivery(long id) {
        var receipt = updates.findById(id).orElseThrow();
        var incoming = json.readValue(receipt.getIncomingJson(), TelegramIncoming.class);
        List<BotReply> replies = receipt.getRepliesJson() == null ? List.of() : json.readValue(receipt.getRepliesJson(),
                json.getTypeFactory().constructCollectionType(List.class, BotReply.class));
        return new Delivery(receipt.getChatId(), incoming.callbackQueryId(), replies, receipt.getDeliveredCount());
    }
    @Transactional
    public void delivered(long id, int count, boolean done) {
        var receipt = updates.lock(id).orElseThrow(); receipt.delivered(count);
        if (done) receipt.finishDelivery(); else receipt.lease(clock.instant().plusSeconds(600));
    }
    @Transactional
    public int expire() { return conversations.expire(clock.instant().minusSeconds(properties.getConversationTimeoutSeconds()), clock.instant(), ConversationPhase.EXPIRED); }
}
