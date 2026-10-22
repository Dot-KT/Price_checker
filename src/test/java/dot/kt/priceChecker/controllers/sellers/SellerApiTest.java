package dot.kt.priceChecker.controllers.sellers;

import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import dot.kt.priceChecker.exceptions.sheets.SheetAccessException;
import dot.kt.priceChecker.entities.sellers.Seller;
import dot.kt.priceChecker.repo.imports.SheetImportRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.repo.sellers.SellerRepository;
import dot.kt.priceChecker.service.sheets.SheetReader;
import dot.kt.priceChecker.service.inventory.InventoryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import dot.kt.priceChecker.repo.users.UserRepository;
import dot.kt.priceChecker.support.TestTokens;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SellerApiTest {
    private static final String ME = "/api/v1/seller/me";
    private static final String SHEET = ME + "/sheet";
    private static final String EMAIL = "seller@example.com";
    private static final String PASSWORD = "a-valid-seller-password";
    private static final String URL = "https://docs.google.com/spreadsheets/d/seller-sheet-123/edit#gid=0";

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired SellerRepository sellers;
    @Autowired UserRepository users;
    @Autowired InventoryItemRepository inventory;
    @Autowired SheetImportRepository imports;
    @Autowired InventoryService inventoryService;
    @MockitoBean SheetReader reader;

    @BeforeEach
    void clearDatabase() {
        imports.deleteAllInBatch();
        inventory.deleteAllInBatch();
        sellers.deleteAllInBatch();
        users.deleteAllInBatch();
        when(reader.read(anyString())).thenAnswer(invocation -> new SheetSnapshot(
                ((String) invocation.getArgument(0)).strip(), "Inventory", List.of(
                List.<Object>of("sku", "name", "price", "condition"),
                List.<Object>of("PHONE-1", "Phone", "100", "Mint"))));
    }

    @Test
    void sellerCanUpdateProfileAndAuthenticateWithNewEmail() throws Exception {
        Seller original = register(EMAIL);
        String originalHash = original.getPasswordHash();
        mvc.perform(put(SHEET).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isOk());

        mvc.perform(put(ME).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(profile("NEW@example.com", "  Updated shop  ", "usd")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.getId().toString()))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.displayName").value("Updated shop"))
                .andExpect(jsonPath("$.defaultCurrency").value("USD"))
                .andExpect(jsonPath("$.googleSheetUrl").value(URL))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Seller updated = sellers.findByEmail("new@example.com").orElseThrow();
        assertThat(updated.getPasswordHash()).isEqualTo(originalHash);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ME).with(bearer("new@example.com", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Updated shop"));
    }

    @Test
    void duplicateEmailAndInvalidCurrencyLeaveProfileUnchanged() throws Exception {
        register(EMAIL);
        register("other@example.com");
        mvc.perform(put(ME).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(profile("OTHER@example.com", "Changed", "NGN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
        mvc.perform(put(ME).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(profile("new@example.com", "Changed", "ZZZ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
        assertThat(sellers.findByEmail(EMAIL).orElseThrow().getDisplayName()).isEqualTo("Test shop");
        assertThat(sellers.existsByEmail("new@example.com")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"email\":\"bad-email\",\"displayName\":\"Shop\",\"defaultCurrency\":\"NGN\"}",
            "{\"email\":\"seller@example.com\",\"displayName\":\"  \",\"defaultCurrency\":\"NGN\"}",
            "{\"email\":\"seller@example.com\",\"displayName\":\"Shop\\u0000\",\"defaultCurrency\":\"NGN\"}",
            "{\"email\":\"seller@example.com\",\"displayName\":\"Shop\"}"
    })
    void invalidProfileIsRejected(String body) throws Exception {
        register(EMAIL);
        mvc.perform(put(ME).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(sellers.findByEmail(EMAIL).orElseThrow().getDisplayName()).isEqualTo("Test shop");
    }

    @Test
    void savingSheetImportsInventoryAndRemovingLinkPreservesDatabaseItems() throws Exception {
        Seller owner = register(EMAIL);
        Seller other = register("other@example.com");
        mvc.perform(put(SHEET).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", "  " + URL + "  "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.googleSheetUrl").value(URL));
        assertThat(sellers.findById(owner.getId()).orElseThrow().getGoogleSheetUrl()).isEqualTo(URL);
        mvc.perform(get(ME).with(bearer(EMAIL, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.googleSheetUrl").value(URL));
        assertThat(sellers.findById(other.getId()).orElseThrow().getGoogleSheetUrl()).isNull();
        assertThat(inventory.findAllBySellerId(owner.getId())).singleElement().satisfies(item -> {
            assertThat(item.getName()).isEqualTo("Phone");
            assertThat(item.getCondition()).isEqualTo("Mint");
            assertThat(item.getPriceMinor()).isEqualTo(10000);
        });
        assertThat(inventory.findAllBySellerId(other.getId())).isEmpty();
        var itemId = inventory.findAllBySellerId(owner.getId()).getFirst().getId();

        String replacement = "https://docs.google.com/spreadsheets/d/replacement/view";
        mvc.perform(put(SHEET).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", replacement))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.googleSheetUrl").value(replacement));
        assertThat(sellers.findById(owner.getId()).orElseThrow().getGoogleSheetUrl()).isEqualTo(replacement);
        mvc.perform(delete(SHEET).with(bearer(EMAIL, PASSWORD))).andExpect(status().isNoContent());
        mvc.perform(delete(SHEET).with(bearer(EMAIL, PASSWORD))).andExpect(status().isNoContent());
        assertThat(sellers.findById(owner.getId()).orElseThrow().getGoogleSheetUrl()).isNull();
        assertThat(imports.count()).isEqualTo(2);
        assertThat(imports.findAll()).allMatch(sheetImport -> sheetImport.getCompletedAt() != null);
        assertThat(inventory.count()).isEqualTo(1);
        assertThat(inventory.findAll().getFirst().getId()).isEqualTo(itemId);
        verify(reader).read("  " + URL + "  ");
        verify(reader).read(replacement);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/sheet", "http://docs.google.com/spreadsheets/d/id/edit",
            "https://docs.google.com.evil.test/spreadsheets/d/id/edit",
            "https://docs.google.com/spreadsheets/d/id/export?format=csv",
            "https://docs.google.com/spreadsheets/d/id/edit#gid=-1"
    })
    void invalidLinksCannotReplaceSavedLink(String invalidUrl) throws Exception {
        register(EMAIL);
        mvc.perform(put(SHEET).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isOk());
        mvc.perform(put(SHEET).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", invalidUrl))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SHEET_INVALID_URL"));
        assertThat(sellers.findByEmail(EMAIL).orElseThrow().getGoogleSheetUrl()).isEqualTo(URL);
        verify(reader).read(URL);
    }

    @Test
    void missingBlankOversizedAndMalformedLinksAreRejected() throws Exception {
        register(EMAIL);
        for (String body : List.of("{}", "{\"url\":null}", "{\"url\":\"  \"}",
                json.writeValueAsString(Map.of("url", "x".repeat(2049))))) {
            mvc.perform(put(SHEET).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(put(SHEET).with(bearer(EMAIL, PASSWORD)).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        verifyNoInteractions(reader);
    }

    @Test
    void savedSheetCanBeReadAndImportedWithoutSubmittingItsUrlAgain() throws Exception {
        register(EMAIL);
        register("other@example.com");
        when(reader.read(URL)).thenReturn(new SheetSnapshot(URL, "Inventory", List.of(
                List.<Object>of("sku", "name", "price"), List.<Object>of("00001", "Phone", "100"))));
        var owner = bearer(EMAIL, PASSWORD);
        mvc.perform(put(SHEET).with(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL)))).andExpect(status().isOk());
        mvc.perform(get(SHEET).with(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUrl").value(URL))
                .andExpect(jsonPath("$.rows[1][0]").value("00001"))
                .andExpect(jsonPath("$.rows[1][1]").value("Phone"));
        assertThat(inventory.count()).isEqualTo(1);
        assertThat(imports.count()).isEqualTo(1);
        var preview = mvc.perform(post("/api/v1/seller/inventory/imports/saved").with(owner))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PREVIEW")).andReturn();
        String id = json.readTree(preview.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/v1/seller/inventory/imports/" + id + "/confirm").with(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/seller/inventory").with(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Phone"));
        var other = bearer("other@example.com", PASSWORD);
        mvc.perform(get(SHEET).with(other)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHEET_NOT_SAVED"));
        mvc.perform(post("/api/v1/seller/inventory/imports/saved").with(other))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SHEET_NOT_SAVED"));
        verify(reader, times(3)).read(URL);
    }

    @Test
    void nonpublicLinksCannotReplaceSavedLinkAndRevokedAccessIsDetectedOnEveryRead() throws Exception {
        register(EMAIL);
        var owner = bearer(EMAIL, PASSWORD);
        mvc.perform(put(SHEET).with(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL)))).andExpect(status().isOk());
        String privateUrl = "https://docs.google.com/spreadsheets/d/private-sheet/edit";
        when(reader.read(privateUrl)).thenThrow(new SheetAccessException(SheetAccessException.Kind.NOT_PUBLIC,
                "Choose Anyone with the link and Viewer."));
        mvc.perform(put(SHEET).with(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", privateUrl))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SHEET_NOT_PUBLIC"));
        assertThat(sellers.findByEmail(EMAIL).orElseThrow().getGoogleSheetUrl()).isEqualTo(URL);
        when(reader.read(URL)).thenThrow(new SheetAccessException(SheetAccessException.Kind.NOT_PUBLIC,
                "Choose Anyone with the link and Viewer."));
        mvc.perform(get(SHEET).with(owner)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SHEET_NOT_PUBLIC"));
        mvc.perform(post("/api/v1/seller/inventory/imports/saved").with(owner))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SHEET_NOT_PUBLIC"));
        assertThat(imports.count()).isEqualTo(1);
        assertThat(inventory.count()).isEqualTo(1);
    }

    @Test
    void invalidSheetRowsLeaveSavedLinkAndInventoryUnchangedAndReturnRowErrors() throws Exception {
        Seller owner = register(EMAIL);
        var token = bearer(EMAIL, PASSWORD);
        mvc.perform(put(SHEET).with(token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isOk());
        var itemId = inventory.findAllBySellerId(owner.getId()).getFirst().getId();
        String invalidUrl = "https://docs.google.com/spreadsheets/d/invalid-sheet/edit";
        when(reader.read(invalidUrl)).thenReturn(new SheetSnapshot(invalidUrl, "Inventory", List.of(
                List.<Object>of("sku", "name", "price"),
                List.<Object>of("GOOD", "Valid phone", "200"),
                List.<Object>of("BAD", "Broken price", "invalid"))));
        mvc.perform(put(SHEET).with(token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", invalidUrl))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SHEET_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.importId").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].rowNumber").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("price"));
        assertThat(sellers.findById(owner.getId()).orElseThrow().getGoogleSheetUrl()).isEqualTo(URL);
        assertThat(inventory.findAll()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(itemId);
            assertThat(item.getPriceMinor()).isEqualTo(10000);
        });
        when(reader.read(invalidUrl)).thenReturn(new SheetSnapshot(invalidUrl, "Inventory", List.of()));
        mvc.perform(put(SHEET).with(token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", invalidUrl))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(inventory.count()).isEqualTo(1);
    }

    @Test
    void deletingInventoryItemOnlyDeletesOwnedDatabaseRowAndDoesNotReadOrWriteSheet() throws Exception {
        Seller owner = register(EMAIL);
        Seller other = register("other@example.com");
        var token = bearer(EMAIL, PASSWORD);
        for (String email : List.of(EMAIL, other.getEmail())) {
            mvc.perform(put(SHEET).with(bearer(email, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("url", URL))))
                    .andExpect(status().isOk());
        }
        var itemId = inventory.findAllBySellerId(owner.getId()).getFirst().getId();
        String path = "/api/v1/seller/inventory/" + itemId;
        clearInvocations(reader);
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        mvc.perform(delete(path).with(bearer(other.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
        assertThat(inventory.existsById(itemId)).isTrue();
        mvc.perform(delete(path).with(token)).andExpect(status().isNoContent());
        assertThat(inventory.existsById(itemId)).isFalse();
        assertThat(inventory.findAllBySellerId(other.getId())).hasSize(1);
        assertThat(imports.count()).isEqualTo(2);
        mvc.perform(get("/api/v1/seller/inventory").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        verifyNoInteractions(reader);
        mvc.perform(get(SHEET).with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[1][0]").value("PHONE-1"));
        mvc.perform(delete(path).with(token)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/seller/inventory/not-a-uuid").with(token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingItemMakesEarlierPreviewStaleButExplicitNewImportCanRestoreIt() throws Exception {
        Seller owner = register(EMAIL);
        var token = bearer(EMAIL, PASSWORD);
        mvc.perform(put(SHEET).with(token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isOk());
        var preview = mvc.perform(post("/api/v1/seller/inventory/imports/saved").with(token))
                .andExpect(status().isCreated()).andReturn();
        String importId = json.readTree(preview.getResponse().getContentAsString()).path("id").asText();
        var itemId = inventory.findAllBySellerId(owner.getId()).getFirst().getId();
        mvc.perform(delete("/api/v1/seller/inventory/" + itemId).with(token)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/seller/inventory/imports/" + importId + "/confirm").with(token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_STALE"));
        assertThat(inventory.count()).isZero();
        mvc.perform(put(SHEET).with(token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isOk());
        assertThat(inventory.findAllBySellerId(owner.getId())).hasSize(1);
    }

    @Test
    void itemDeletionDuringSheetDownloadPreventsSaveFromRestoringAnOutdatedSnapshot() throws Exception {
        Seller owner = register(EMAIL);
        var token = bearer(EMAIL, PASSWORD);
        mvc.perform(put(SHEET).with(token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isOk());
        var itemId = inventory.findAllBySellerId(owner.getId()).getFirst().getId();
        String replacement = "https://docs.google.com/spreadsheets/d/replacement/edit";
        when(reader.read(replacement)).thenAnswer(invocation -> {
            inventoryService.delete(itemId);
            return new SheetSnapshot(replacement, "Inventory", List.of(
                    List.<Object>of("sku", "name", "price"),
                    List.<Object>of("PHONE-1", "Phone", "100")));
        });
        mvc.perform(put(SHEET).with(token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", replacement))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_STALE"));
        assertThat(inventory.count()).isZero();
        assertThat(sellers.findById(owner.getId()).orElseThrow().getGoogleSheetUrl()).isEqualTo(URL);
    }

    @Test
    void profileAndLinkOperationsRequireAuthentication() throws Exception {
        mvc.perform(get(ME)).andExpect(status().isUnauthorized());
        mvc.perform(get(SHEET)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/seller/inventory/imports/saved")).andExpect(status().isUnauthorized());
        mvc.perform(put(ME).contentType(MediaType.APPLICATION_JSON).content(profile(EMAIL, "Shop", "NGN")))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(ME)).andExpect(status().isUnauthorized());
        mvc.perform(put(SHEET).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(SHEET)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reader);
    }

    @Test
    void deletingAccountRemovesOwnedImportsAndInventoryAndPreservesOtherSeller() throws Exception {
        Seller owner = register(EMAIL);
        RequestPostProcessor ownerToken = bearer(EMAIL, PASSWORD);
        Seller other = register("other@example.com");
        when(reader.read(URL)).thenReturn(new SheetSnapshot(URL, "Inventory",
                List.of(List.<Object>of("sku", "name", "price"), List.<Object>of("PHONE-1", "Phone", "100"))));
        for (Seller seller : List.of(owner, other)) {
            var preview = mvc.perform(post("/api/v1/seller/inventory/imports")
                            .with(bearer(seller.getEmail(), PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("url", URL))))
                    .andExpect(status().isCreated()).andReturn();
            String id = json.readTree(preview.getResponse().getContentAsString()).path("id").asText();
            mvc.perform(post("/api/v1/seller/inventory/imports/" + id + "/confirm")
                            .with(bearer(seller.getEmail(), PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"confirm\":true}"))
                    .andExpect(status().isOk());
        }

        mvc.perform(delete(ME).with(bearer(EMAIL, PASSWORD))).andExpect(status().isNoContent());
        assertThat(sellers.existsById(owner.getId())).isFalse();
        assertThat(inventory.findAllBySellerId(owner.getId())).isEmpty();
        assertThat(imports.findAll()).allMatch(sheetImport -> sheetImport.getSeller().getId().equals(other.getId()));
        assertThat(imports.count()).isEqualTo(1);
        assertThat(inventory.findAllBySellerId(other.getId())).hasSize(1);
        mvc.perform(get(ME).with(ownerToken)).andExpect(status().isUnauthorized());
        mvc.perform(get(ME).with(bearer(other.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(other.getId().toString()));
    }

    private RequestPostProcessor bearer(String email, String password) throws Exception {
        return TestTokens.bearer(mvc, json, email, password);
    }

    private Seller register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "displayName", "Test shop"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        return sellers.findByEmail(email).orElseThrow();
    }

    private String profile(String email, String name, String currency) {
        return json.writeValueAsString(Map.of("email", email, "displayName", name, "defaultCurrency", currency));
    }
}
