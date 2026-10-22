package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.dto.telegram.*;
import dot.kt.priceChecker.entities.catalog.*;
import dot.kt.priceChecker.entities.inventory.InventoryItem;
import dot.kt.priceChecker.entities.sellers.Seller;
import dot.kt.priceChecker.entities.users.AppUser;
import dot.kt.priceChecker.enums.catalog.*;
import dot.kt.priceChecker.enums.security.Role;
import dot.kt.priceChecker.enums.telegram.ConversationPhase;
import dot.kt.priceChecker.repo.catalog.ItemRepository;
import dot.kt.priceChecker.repo.imports.SheetImportRepository;
import dot.kt.priceChecker.repo.intel.PriceIntelRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.repo.sellers.SellerRepository;
import dot.kt.priceChecker.repo.telegram.*;
import dot.kt.priceChecker.repo.users.UserRepository;
import dot.kt.priceChecker.service.intel.PriceIntelService;
import dot.kt.priceChecker.support.TestTokens;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:telegram_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TelegramFlowTest.TimeConfiguration.class)
class TelegramFlowTest {
    static final long TELEGRAM_ID = 5_000_000_001L;
    static final String PASSWORD = "a-valid-test-password";
    @TestConfiguration static class TimeConfiguration { @Bean @Primary MutableClock botTestClock() { return new MutableClock(); } }
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-22T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @Autowired TelegramConversationStore store;
    @Autowired TelegramConversationService service;
    @Autowired TelegramUserRepository telegramUsers;
    @Autowired TelegramConversationRepository conversations;
    @Autowired TelegramUpdateRepository updates;
    @Autowired ItemRepository items;
    @Autowired InventoryItemRepository inventory;
    @Autowired SellerRepository sellers;
    @Autowired UserRepository users;
    @Autowired SheetImportRepository imports;
    @Autowired PriceIntelRepository intel;
    @Autowired PriceIntelService prices;
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired PasswordEncoder passwords;
    @MockitoBean ProductAssistant assistant;
    Gadget phone;
    InventoryItem first;
    long updateId;
    List<BotReply> last;

    @BeforeEach void setUp() {
        clean(); clock.now = Instant.parse("2026-10-22T10:00:00Z"); updateId = 0;
        users.saveAndFlush(new AppUser("admin@example.com", passwords.encode(PASSWORD), Role.ADMIN));
        users.saveAndFlush(new AppUser("manager@example.com", passwords.encode(PASSWORD), Role.MANAGER));
        phone = gadget("Samsung Galaxy S24 Ultra", "S24 Ultra", GadgetType.PHONE);
        first = offer(phone, "first", 20_000_000L, "NGN", 2, "Used", true);
        offer(phone, "second", 40_000_000L, "NGN", null, "Mint", true);
        offer(phone, "empty", 1, "NGN", 0, "Used", true);
        offer(phone, "disabled", 1, "NGN", 2, "Used", false);
        offer(phone, "dollars", 50000, "USD", 1, "Brand new", true);
        when(assistant.identify(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return question(List.of(phone.getName()), true, false, false);
        });
        when(assistant.explain(any(), anyList())).thenReturn(new SpecificationReport("Product specifications from current retrieved sources.", "<table><tr><th>Specification</th><td>Verified sample</td></tr></table>"));
    }
    @AfterEach void clean() {
        updates.deleteAllInBatch(); conversations.deleteAllInBatch(); telegramUsers.deleteAllInBatch(); intel.deleteAllInBatch();
        imports.deleteAllInBatch(); inventory.deleteAllInBatch(); sellers.deleteAllInBatch(); users.deleteAllInBatch(); items.deleteAllInBatch();
    }
    @Test void fullConversationSavesProfileAndSelectionAndHistorySurvivesListingDeletion() throws Exception {
        startPhone();
        var profile = telegramUsers.findByTelegramId(TELEGRAM_ID).orElseThrow();
        assertThat(profile.getRealName()).isEqualTo("Ada Okafor");
        assertThat(profile.getUsername()).isEqualTo("ada"); assertThat(profile.getSelectedItem()).isNull();
        send("How much is the S24 Ultra?");
        assertThat(text()).contains("NGN 300000.00", "Seller average", "2 offers from 2 sellers");
        click("Show sellers");
        assertThat(text()).contains("Used", "Mint", "first", "128 GB", "NGN 200000.00");
        assertThat(last).anyMatch(reply -> reply.richHtml() != null && reply.richHtml().contains("<table"));
        click("Select this offer");
        assertThat(text()).contains("Saved your selection", "Ada Okafor");
        assertThat(last.getFirst().buttons().getFirst().getFirst().url()).isEqualTo(BotInformationService.HANDOFF_URL);
        profile = telegramUsers.findByTelegramId(TELEGRAM_ID).orElseThrow();
        assertThat(profile.getSelectedItem().getId()).isEqualTo(phone.getId());
        assertThat(profile.getSelectedListing().getId()).isEqualTo(first.getId());
        inventory.deleteById(first.getId()); inventory.flush();
        var auth = TestTokens.bearer(mvc, json, "admin@example.com", PASSWORD);
        mvc.perform(get("/api/v1/management/telegram/users").param("q", "Ada").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].selection.sellerName").value("first"))
                .andExpect(jsonPath("$.items[0].selectedListingId").isEmpty());
        send("Hi again"); assertThat(text()).contains("What's your original name?");
        assertThat(telegramUsers.findByTelegramId(TELEGRAM_ID).orElseThrow().getSelectedItem()).isNull();
        mvc.perform(get("/api/v1/management/telegram/users/" + TELEGRAM_ID + "/conversations").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[?(@.phase == 'COMPLETED')].selection.sellerName").value(org.hamcrest.Matchers.hasItem("first")));
    }
    @Test void timeoutAndCancelResetConversationAndOldButtonsCannotSelectItems() {
        startPhone(); send("price"); String old = callback("Show sellers");
        clock.now = clock.now.plusSeconds(3600);
        send("Hi"); assertThat(text()).contains("What's your original name?");
        assertThat(conversations.findAll()).anyMatch(c -> c.getPhase() == ConversationPhase.EXPIRED);
        send("Ada"); callbackInput(old); assertThat(text()).contains("earlier conversation");
        send("cancel"); assertThat(text()).contains("cancelled");
        send("price"); assertThat(text()).contains("What's your original name?");
        assertThat(conversations.count()).isEqualTo(3);
        verify(assistant, times(1)).identify(any());
    }
    @Test void schedulerExpiresIdleConversationAndNextMessageStartsFresh() {
        startPhone(); clock.now = clock.now.plusSeconds(3601); assertThat(store.expire()).isEqualTo(1);
        send("How much?"); assertThat(text()).contains("What's your original name?");
    }
    @Test void pollingAcceptsRandomizedUpdateIdsAfterAnIdleWeek() {
        startPhone(); assertThat(store.offset()).isEqualTo(updateId + 1);
        clock.now = clock.now.plus(Duration.ofDays(7)); assertThat(store.offset()).isZero();
    }
    @Test void duplicateUpdatesAreProcessedOnceAndPendingMessagesStayInChatOrder() {
        startPhone(); send("price");
        long id = updateId; long count = conversations.count();
        store.receive(new TelegramIncoming(id, TELEGRAM_ID, TELEGRAM_ID, "different", null, null, "cancel", null, null, true));
        service.process(id); assertThat(conversations.count()).isEqualTo(count);
        verify(assistant, times(1)).identify(any());
        receive("specs"); long firstPending = updateId; receive("cancel");
        assertThat(store.pending()).extracting(TelegramConversationStore.Pending::updateId).containsExactly(firstPending);
        assertThat(store.claim(firstPending)).isTrue(); assertThat(store.claim(firstPending)).isFalse();
        assertThat(store.pending()).isEmpty();
        clock.now = clock.now.plusSeconds(601);
        assertThat(store.pending()).extracting(TelegramConversationStore.Pending::updateId).containsExactly(firstPending);
    }
    @Test void intelOverridesAverageAndIsAdminOnlyWhilePublicReadsHideNotes() throws Exception {
        var auth = TestTokens.bearer(mvc, json, "admin@example.com", PASSWORD);
        String path = "/api/v1/management/items/" + phone.getId() + "/price-intel";
        mvc.perform(put(path).with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"price\":275000.25,\"currency\":\"NGN\",\"notes\":\"Internal source\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priceMinor").value(27500025));
        mvc.perform(get("/api/v1/catalog/GADGET/items/" + phone.getId() + "/price"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("Price checker specified intel"))
                .andExpect(jsonPath("$.notes").isEmpty());
        mvc.perform(put(path).with(TestTokens.bearer(mvc, json, "manager@example.com", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content("{\"price\":1,\"currency\":\"NGN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/management/telegram/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/index.html")).andExpect(status().isOk());
        startPhone(); send("price"); assertThat(text()).contains("NGN 275000.25", "Price checker specified intel");
        mvc.perform(delete(path).with(auth)).andExpect(status().isNoContent());
        assertThat(prices.quote(phone.getId(), "NGN", null).priceMinor()).isEqualTo(30_000_000L);
        mvc.perform(put(path).with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"price\":1.001,\"currency\":\"NGN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(path).with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"price\":-1,\"currency\":\"NGN\"}"))
                .andExpect(status().isBadRequest());
        assertThat(prices.quote(phone.getId(), "NGN", "used").priceMinor()).isEqualTo(20_000_000L);
        assertThat(prices.quote(phone.getId(), "USD", null).priceMinor()).isEqualTo(50000L);
        assertThat(prices.quote(phone.getId(), "EUR", null).priceMinor()).isNull();
    }
    @Test void comparesTwoItemsAndKeepsBothAfterResolvingAmbiguousModel() {
        gadget("Samsung Galaxy S24 Plus", "S24 Plus", GadgetType.PHONE);
        Gadget other = gadget("Samsung Galaxy A55", "A55", GadgetType.PHONE);
        offer(other, "a55", 15_000_000, "NGN", 1, "New", true);
        when(assistant.identify(any())).thenReturn(question(List.of("Samsung Galaxy S24", other.getName()), true, true, true));
        startPhone(); send("Compare S24 and A55 with prices"); assertThat(text()).contains("Which exact model");
        click(phone.getName());
        assertThat(text()).contains(phone.getName(), other.getName(), "NGN 300000.00", "NGN 150000.00");
        verify(assistant).explain(any(), eq(List.of(phone.getName(), other.getName())));
        click("Show sellers"); assertThat(text()).contains("first", "a55");
    }
    @Test void storageVariantInTheModelColumnIsRecognizedAndConfirmedBeforeQuoting() {
        Gadget small = gadget(phone.getName(), "128GB", GadgetType.PHONE);
        Gadget large = gadget(phone.getName(), "256GB", GadgetType.PHONE);
        offer(small, "small", 10_000_000L, "NGN", 1, "New", true);
        offer(large, "large", 35_000_000L, "NGN", 1, "New", true);
        when(assistant.identify(any())).thenReturn(question(List.of("Samsung Galaxy S24 Ultra 256GB"), true, false, false));
        startPhone(); send("S24 Ultra 256GB price"); assertThat(text()).contains("Which exact model");
        click(phone.getName() + " [256GB]");
        assertThat(text()).contains("NGN 350000.00", "256GB").doesNotContain("NGN 100000.00");
    }
    @Test void unknownItemGetsKnowledgeAndHandoffWithoutInventingOffers() {
        when(assistant.identify(any())).thenReturn(question(List.of("Unknown Phone 99"), true, true, false));
        startPhone(); send("Unknown Phone 99 specs and price");
        assertThat(text()).contains("Specifications:", "aren't in our database", BotInformationService.HANDOFF_URL)
                .doesNotContain("Seller average");
        assertThat(last.stream().flatMap(r -> r.buttons().stream()).flatMap(List::stream)).isEmpty();
    }
    @Test void failedOllamaLeavesConversationUsableAndFollowupsCarryPreviousItems() {
        startPhone(); send("price");
        when(assistant.identify(any())).thenThrow(new IllegalStateException("offline"));
        send("specs"); assertThat(text()).contains("couldn't reach Ollama");
        doReturn(question(List.of(phone.getName()), false, true, false)).when(assistant).identify(any());
        send("try again"); assertThat(text()).contains("Specifications:");
        var captor = ArgumentCaptor.forClass(AssistantWork.class); verify(assistant, times(3)).identify(captor.capture());
        assertThat(captor.getAllValues().getLast().previousItems()).containsExactly(phone.getName());
    }
    @Test void scopePreventsMatchingAppliancesOrOtherGadgetTypesAndGroupChatsAreIgnored() {
        Gadget laptop = gadget("A laptop", "L1", GadgetType.LAPTOP);
        when(assistant.identify(any())).thenReturn(question(List.of(laptop.getName()), true, false, false));
        startPhone(); send("A laptop price"); assertThat(text()).contains("aren't in our database");
        store.receive(new TelegramIncoming(++updateId, 45, -100, "group", "User", null, "hi", null, null, false));
        service.process(updateId); assertThat(store.delivery(updateId).replies()).isEmpty();
        assertThat(telegramUsers.findByTelegramId(45)).isEmpty();
    }
    @Test void missingOrTamperedOfferCannotBeSelected() {
        startPhone(); send("price"); click("Show sellers");
        String valid = callback("Select this offer");
        callbackInput(valid.substring(0, valid.lastIndexOf(':') + 1) + "999999");
        assertThat(telegramUsers.findByTelegramId(TELEGRAM_ID).orElseThrow().getSelectedItem()).isNull();
        inventory.deleteById(first.getId()); inventory.flush(); callbackInput(valid);
        assertThat(text()).contains("no longer available");
        send("yes"); assertThat(text()).contains("second").doesNotContain("first");
    }
    @Test void oldButtonsInSameConversationAndChangedPricesCannotSilentlySelectAnotherOffer() {
        startPhone(); send("price"); String oldPrompt = callback("Show sellers"); click("Show sellers");
        String choose = callback("Select this offer");
        first.update(first.getName(), first.getModel(), first.getDescription(), first.getCondition(), 25_000_000L, "NGN", 2, 2);
        inventory.saveAndFlush(first); callbackInput(choose);
        assertThat(text()).contains("has changed");
        assertThat(telegramUsers.findByTelegramId(TELEGRAM_ID).orElseThrow().getSelectedItem()).isNull();
        send("yes"); assertThat(text()).contains("NGN 250000.00");
        callbackInput(oldPrompt); assertThat(text()).contains("older step");
        callbackInput(choose); assertThat(text()).contains("older step");
    }
    @Test void appliancesAndNonPhoneGadgetsHaveTheirOwnConversationScope() {
        send("hi"); send("Ada"); click("Appliances"); send("Hisense refrigerator specs");
        var captor = ArgumentCaptor.forClass(AssistantWork.class); verify(assistant).identify(captor.capture());
        assertThat(captor.getValue().domain()).isEqualTo(ItemDomain.ELECTRICAL_APPLIANCE);
        assertThat(captor.getValue().gadgetType()).isNull();
        send("cancel"); send("hi"); send("Ada"); click("Gadgets"); click("LAPTOP"); send("Laptop specs");
        verify(assistant, times(2)).identify(captor.capture());
        assertThat(captor.getValue().gadgetType()).isEqualTo(GadgetType.LAPTOP);
    }
    private void startPhone() { send("hi"); assertThat(text()).contains("Ch3cker"); send("Ada Okafor"); click("Gadgets"); click("PHONE"); }
    private void receive(String text) { store.receive(new TelegramIncoming(++updateId, TELEGRAM_ID, TELEGRAM_ID, "ada", "Ada", "Okafor", text, null, null, true)); }
    private void send(String text) { receive(text); process(); }
    private void callbackInput(String data) {
        assertThat(data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
        store.receive(new TelegramIncoming(++updateId, TELEGRAM_ID, TELEGRAM_ID, "ada", "Ada", "Okafor", null, data, "callback", true)); process();
    }
    private void click(String label) { callbackInput(callback(label)); }
    private String callback(String label) { return last.stream().flatMap(reply -> reply.buttons().stream()).flatMap(List::stream)
            .filter(button -> button.text().equals(label)).findFirst().orElseThrow().callbackData(); }
    private void process() { service.process(updateId); var delivery = store.delivery(updateId); last = delivery.replies(); store.delivered(updateId, last.size(), true); }
    private String text() { return last.stream().map(BotReply::text).reduce("", (a, b) -> a + "\n" + b); }
    private Gadget gadget(String name, String model, GadgetType type) { return items.saveAndFlush(new Gadget(name, "Samsung", model, type == GadgetType.PHONE ? "S" : null, type, UUID.randomUUID().toString())); }
    private InventoryItem offer(Item item, String name, long price, String currency, Integer quantity, String condition, boolean enabled) {
        AppUser user = new AppUser(name + "@example.com", "unused-hash", Role.SELLER); user.setEnabled(enabled);
        Seller seller = sellers.saveAndFlush(new Seller(user, name, currency));
        InventoryItem listing = new InventoryItem(seller, "SKU", item.getName(), item.getModel(), "128 GB unlocked", condition, price, currency, quantity, 2);
        listing.assignItem(item); return inventory.saveAndFlush(listing);
    }
    private static QuestionIntent question(List<String> names, boolean price, boolean details, boolean comparison) { return new QuestionIntent(names, price, details, comparison, "NGN", null, null, false); }
}
