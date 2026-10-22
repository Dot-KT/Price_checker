package dot.kt.priceChecker.controllers.imports;

import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import dot.kt.priceChecker.entities.imports.SheetImport;
import dot.kt.priceChecker.entities.inventory.InventoryItem;
import dot.kt.priceChecker.entities.sellers.Seller;
import dot.kt.priceChecker.enums.imports.ImportStatus;
import dot.kt.priceChecker.exceptions.sheets.SheetAccessException;
import dot.kt.priceChecker.repo.imports.SheetImportRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.repo.sellers.SellerRepository;
import dot.kt.priceChecker.service.sheets.SheetReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import dot.kt.priceChecker.repo.users.UserRepository;
import dot.kt.priceChecker.support.TestTokens;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SheetImportApiTest {
    private static final String IMPORTS = "/api/v1/seller/inventory/imports";
    private static final String INVENTORY = "/api/v1/seller/inventory";
    private static final String URL = "https://docs.google.com/spreadsheets/d/seller-sheet-123/edit#gid=0";
    private static final String PASSWORD = "a-valid-seller-password";

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired SellerRepository sellers;
    @Autowired UserRepository users;
    @Autowired InventoryItemRepository inventory;
    @Autowired SheetImportRepository imports;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean SheetReader reader;

    @BeforeEach
    void clearDatabase() {
        // Do not wrap these tests in a transaction: HTTP confirmations must commit or roll back themselves.
        imports.deleteAllInBatch();
        inventory.deleteAllInBatch();
        sellers.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void registrationStoresOnlyAnEncodedPasswordAndRejectsDuplicateAccounts() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "SELLER@example.com", "password", PASSWORD,
                                "displayName", "Lagos Phones", "defaultCurrency", "ngn"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("seller@example.com"))
                .andExpect(jsonPath("$.defaultCurrency").value("NGN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();

        Seller seller = sellers.findByEmail("seller@example.com").orElseThrow();
        assertThat(seller.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, seller.getPasswordHash())).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD, seller.getPasswordHash());

        mvc.perform(get("/api/v1/seller/me").with(bearer("seller@example.com", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(seller.getId().toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "seller@example.com", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "seller@example.com", "password", PASSWORD,
                                "displayName", "Duplicate"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "invalid@example.com", "password", PASSWORD,
                                "displayName", "Invalid\u0000Seller"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(sellers.count()).isEqualTo(1);
    }

    @Test
    void previewRequiresConfirmationAndRepeatedConfirmKeepsInventoryIdsAndPrices() throws Exception {
        Seller seller = register("first@example.com");
        mockSheet(row("sku", "name", "price", "currency", "quantity", "condition"),
                row("PHONE-1", "Phone", "1,234.50", "NGN", "2", "Mint"),
                row("CABLE-1", "Cable", "0.10", "NGN", "0", ""));

        JsonNode preview = preview(seller);
        String id = preview.path("id").asText();
        assertThat(preview.path("status").asText()).isEqualTo("PREVIEW");
        assertThat(preview.path("totalRows").intValue()).isEqualTo(2);
        assertThat(preview.path("validRows").intValue()).isEqualTo(2);
        assertThat(preview.path("errors").isEmpty()).isTrue();
        assertThat(preview.path("preview").get(0).path("condition").asText()).isEqualTo("Mint");
        assertThat(inventory.count()).isZero();
        mvc.perform(get(IMPORTS + "/" + id).with(bearer(seller.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREVIEW"));
        mvc.perform(get(INVENTORY).with(bearer(seller.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        clearInvocations(reader);
        confirm(seller, id);
        InventoryItem phone = item(seller, "PHONE-1");
        InventoryItem cable = item(seller, "CABLE-1");
        assertThat(phone.getPriceMinor()).isEqualTo(123450L);
        assertThat(phone.getCurrency()).isEqualTo("NGN");
        assertThat(phone.getQuantity()).isEqualTo(2);
        assertThat(phone.getCondition()).isEqualTo("Mint");
        assertThat(cable.getCondition()).isNull();
        assertThat(cable.getPriceMinor()).isEqualTo(10L);
        assertThat(cable.getQuantity()).isZero();
        UUID phoneId = phone.getId();
        UUID cableId = cable.getId();
        confirm(seller, id);
        assertThat(item(seller, "PHONE-1").getId()).isEqualTo(phoneId);
        assertThat(item(seller, "CABLE-1").getId()).isEqualTo(cableId);
        assertThat(inventory.count()).isEqualTo(2);
        verifyNoInteractions(reader);
        mvc.perform(get(INVENTORY).with(bearer(seller.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].sellerSku").value("CABLE-1"))
                .andExpect(jsonPath("$.items[0].priceMinor").value(10))
                .andExpect(jsonPath("$.items[1].id").value(phoneId.toString()))
                .andExpect(jsonPath("$.items[1].condition").value("Mint"));
    }

    @Test
    void replacementUpsertsOwnSkusDeactivatesMissingItemsAndPreservesOtherSellers() throws Exception {
        Seller first = register("first@example.com");
        Seller second = register("second@example.com");
        mockSheet(row("sku", "name", "price", "condition"), row("SHARED", "First phone", "100.10", "Used"),
                row("REMOVED", "Old cable", "25", "Used"));
        confirm(first, preview(first).path("id").asText());
        UUID sharedId = item(first, "SHARED").getId();
        UUID removedId = item(first, "REMOVED").getId();
        mockSheet(row("sku", "name", "price"), row("SHARED", "Second phone", "500"));
        confirm(second, preview(second).path("id").asText());
        UUID secondId = item(second, "SHARED").getId();

        mockSheet(row("sku", "name", "price", "condition"), row("SHARED", "Updated first phone", "120.25", "Has issues: cracked screen"),
                row("NEW", "New charger", "50", "Brand new"));
        String replacement = preview(first).path("id").asText();
        assertThat(item(first, "SHARED").getName()).isEqualTo("First phone");
        assertThat(item(first, "REMOVED").isActive()).isTrue();
        confirm(first, replacement);

        assertThat(item(first, "SHARED").getId()).isEqualTo(sharedId);
        assertThat(item(first, "SHARED").getPriceMinor()).isEqualTo(12025L);
        assertThat(item(first, "SHARED").getCondition()).isEqualTo("Has issues: cracked screen");
        assertThat(item(first, "NEW").getCondition()).isEqualTo("Brand new");
        assertThat(item(second, "SHARED").getCondition()).isNull();
        assertThat(item(first, "REMOVED").getId()).isEqualTo(removedId);
        assertThat(item(first, "REMOVED").isActive()).isFalse();
        assertThat(item(first, "NEW").isActive()).isTrue();
        assertThat(item(second, "SHARED").getId()).isEqualTo(secondId);
        assertThat(item(second, "SHARED").getName()).isEqualTo("Second phone");
        assertThat(item(second, "SHARED").getPriceMinor()).isEqualTo(50000L);
        mvc.perform(get(INVENTORY).with(bearer(first.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].sellerSku").value("NEW"))
                .andExpect(jsonPath("$.items[1].sellerSku").value("SHARED"));
        mvc.perform(get(INVENTORY).with(bearer(second.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(secondId.toString()));

        // Reintroducing an item reactivates its original record instead of creating another SKU.
        mockSheet(row("sku", "name", "price"), row("REMOVED", "Cable back in stock", "30"));
        confirm(first, preview(first).path("id").asText());
        assertThat(item(first, "REMOVED").getId()).isEqualTo(removedId);
        assertThat(item(first, "REMOVED").isActive()).isTrue();
        assertThat(item(first, "REMOVED").getCondition()).isNull();
        assertThat(item(first, "SHARED").isActive()).isFalse();
        assertThat(item(second, "SHARED").isActive()).isTrue();
    }

    @Test
    void previewsSavedBeforeConditionWasAddedStillLoadAndConfirm() throws Exception {
        Seller seller = register("legacy@example.com");
        String legacyItems = json.writeValueAsString(List.of(Map.of(
                "sellerSku", "PHONE", "name", "Phone", "priceMinor", 10000,
                "currency", "NGN", "sourceRow", 2)));
        SheetImport legacy = imports.saveAndFlush(new SheetImport(seller, URL, "Inventory", 1, 1, 0, legacyItems, "[]"));
        mvc.perform(get(IMPORTS + "/" + legacy.getId()).with(bearer(seller.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREVIEW"));
        confirm(seller, legacy.getId().toString());
        assertThat(item(seller, "PHONE").getCondition()).isNull();
        assertThat(item(seller, "PHONE").getPriceMinor()).isEqualTo(10000);
    }

    @Test
    void anyInvalidRowBlocksAllWritesAndLeavesCurrentInventoryAvailable() throws Exception {
        Seller seller = register("first@example.com");
        mockSheet(row("sku", "name", "price"), row("EXISTING", "Current phone", "100"));
        confirm(seller, preview(seller).path("id").asText());
        UUID existingId = item(seller, "EXISTING").getId();

        mockSheet(row("sku", "name", "price"), row("GOOD", "Valid item", "10"),
                row("BAD", "Invalid item", "not-a-price"));
        JsonNode invalid = preview(seller);
        assertThat(invalid.path("status").asText()).isEqualTo("INVALID");
        assertThat(invalid.path("validRows").intValue()).isEqualTo(1);
        assertThat(invalid.path("errorRows").intValue()).isEqualTo(1);
        assertThat(invalid.path("errors").get(0).path("rowNumber").intValue()).isEqualTo(3);
        assertThat(invalid.path("errors").get(0).path("field").asText()).isEqualTo("price");
        mvc.perform(post(IMPORTS + "/" + invalid.path("id").asText() + "/confirm")
                        .with(bearer(seller.getEmail(), PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_INVALID"));
        assertThat(inventory.count()).isEqualTo(1);
        assertThat(item(seller, "EXISTING").getId()).isEqualTo(existingId);
        assertThat(item(seller, "EXISTING").isActive()).isTrue();
        assertThat(item(seller, "EXISTING").getPriceMinor()).isEqualTo(10000L);
    }

    @Test
    void emptySheetCannotEraseInventoryAndStructuralErrorsStayInvalid() throws Exception {
        Seller seller = register("first@example.com");
        mockSheet(row("name", "price"), row("Current phone", "100"));
        confirm(seller, preview(seller).path("id").asText());
        mockSheet(row("name", "price"));
        String emptyId = preview(seller).path("id").asText();
        mvc.perform(post(IMPORTS + "/" + emptyId + "/confirm").with(bearer(seller.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_INVALID"));

        mockSheet(row("unsupported", "headers"));
        JsonNode invalid = preview(seller);
        assertThat(invalid.path("status").asText()).isEqualTo("INVALID");
        assertThat(invalid.path("errors").isEmpty()).isFalse();
        mvc.perform(post(IMPORTS + "/" + invalid.path("id").asText() + "/confirm")
                        .with(bearer(seller.getEmail(), PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_INVALID"));
        assertThat(inventory.count()).isEqualTo(1);
        assertThat(inventory.findAll().getFirst().isActive()).isTrue();
    }

    @Test
    void sellersCannotReadOrConfirmAnotherSellersPreview() throws Exception {
        Seller first = register("first@example.com");
        Seller second = register("second@example.com");
        mockSheet(row("name", "price"), row("Phone", "100"));
        String id = preview(first).path("id").asText();

        mvc.perform(get(IMPORTS + "/" + id).with(bearer(second.getEmail(), PASSWORD)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));
        mvc.perform(post(IMPORTS + "/" + id + "/confirm").with(bearer(second.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));
        assertThat(inventory.count()).isZero();
        assertThat(imports.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(ImportStatus.PREVIEW);
        confirm(first, id);
        mvc.perform(get(INVENTORY).with(bearer(second.getEmail(), PASSWORD)).param("sellerId", first.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void suppliedSellerIdCannotRedirectImportOwnership() throws Exception {
        Seller first = register("first@example.com");
        Seller second = register("second@example.com");
        mockSheet(row("name", "price"), row("My phone", "100"));
        MvcResult result = mvc.perform(post(IMPORTS).with(bearer(first.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL, "sellerId", second.getId()))))
                .andExpect(status().isCreated()).andReturn();
        String id = body(result).path("id").asText();
        confirm(first, id);
        assertThat(inventory.findAllBySellerId(first.getId())).hasSize(1);
        assertThat(inventory.findAllBySellerId(second.getId())).isEmpty();
    }

    @Test
    void stalePreviewCannotOverwriteANewerCompletedImport() throws Exception {
        Seller seller = register("first@example.com");
        mockSheet(row("sku", "name", "price"), row("PHONE", "Old preview", "100"));
        String stale = preview(seller).path("id").asText();
        mockSheet(row("sku", "name", "price"), row("PHONE", "New import", "250"));
        String current = preview(seller).path("id").asText();
        confirm(seller, current);

        mvc.perform(post(IMPORTS + "/" + stale + "/confirm").with(bearer(seller.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_STALE"));
        assertThat(item(seller, "PHONE").getName()).isEqualTo("New import");
        assertThat(item(seller, "PHONE").getPriceMinor()).isEqualTo(25000L);
        assertThat(imports.findById(UUID.fromString(stale)).orElseThrow().getStatus()).isEqualTo(ImportStatus.PREVIEW);
        confirm(seller, current);
    }

    @Test
    void missingAuthenticationCannotReadOrChangeSellerData() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get(INVENTORY)).andExpect(status().isUnauthorized());
        mvc.perform(get(IMPORTS + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post(IMPORTS).contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"" + URL + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(IMPORTS + "/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reader);
    }

    @Test
    void invalidRequestBodiesAndMissingConfirmationDoNotModifyInventory() throws Exception {
        Seller seller = register("first@example.com");
        mvc.perform(post(IMPORTS).with(bearer(seller.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(IMPORTS).with(bearer(seller.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(IMPORTS).with(bearer(seller.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        verifyNoInteractions(reader);

        mockSheet(row("name", "price"), row("Phone", "100"));
        String id = preview(seller).path("id").asText();
        for (String confirmation : List.of("{}", "{\"confirm\":false}")) {
            mvc.perform(post(IMPORTS + "/" + id + "/confirm").with(bearer(seller.getEmail(), PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(inventory.count()).isZero();
        assertThat(imports.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(ImportStatus.PREVIEW);
    }

    @ParameterizedTest
    @CsvSource({"INVALID_URL,400", "NOT_PUBLIC,422", "NOT_FOUND,422", "LIMIT_EXCEEDED,422", "UPSTREAM,502"})
    void sheetFailuresReturnActionableErrorsWithoutCreatingImports(String kind, int expectedStatus) throws Exception {
        Seller seller = register("first@example.com");
        when(reader.read(anyString())).thenThrow(new SheetAccessException(SheetAccessException.Kind.valueOf(kind),
                "The spreadsheet could not be read."));
        mvc.perform(post(IMPORTS).with(bearer(seller.getEmail(), PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value("SHEET_" + kind))
                .andExpect(jsonPath("$.detail").value("The spreadsheet could not be read."));
        assertThat(imports.count()).isZero();
        assertThat(inventory.count()).isZero();
    }

    private RequestPostProcessor bearer(String email, String password) throws Exception {
        return TestTokens.bearer(mvc, json, email, password);
    }

    private Seller register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "displayName", "Test seller", "defaultCurrency", "NGN"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        return sellers.findByEmail(email).orElseThrow();
    }

    private JsonNode preview(Seller seller) throws Exception {
        return body(mvc.perform(post(IMPORTS).with(bearer(seller.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isCreated()).andReturn());
    }

    private void confirm(Seller seller, String id) throws Exception {
        mvc.perform(post(IMPORTS + "/" + id + "/confirm").with(bearer(seller.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    private InventoryItem item(Seller seller, String sku) {
        return inventory.findAllBySellerId(seller.getId()).stream()
                .filter(item -> item.getSellerSku().equals(sku)).findFirst().orElseThrow();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @SafeVarargs
    private void mockSheet(List<Object>... rows) {
        when(reader.read(URL)).thenReturn(new SheetSnapshot(URL, "Inventory", List.of(rows)));
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}
