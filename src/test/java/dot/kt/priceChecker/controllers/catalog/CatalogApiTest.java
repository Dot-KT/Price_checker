package dot.kt.priceChecker.controllers.catalog;

import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import dot.kt.priceChecker.entities.catalog.ElectricalAppliance;
import dot.kt.priceChecker.entities.catalog.Gadget;
import dot.kt.priceChecker.entities.sellers.Seller;
import dot.kt.priceChecker.entities.users.AppUser;
import dot.kt.priceChecker.enums.security.Role;
import dot.kt.priceChecker.repo.catalog.ItemRepository;
import dot.kt.priceChecker.repo.imports.SheetImportRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.repo.sellers.SellerRepository;
import dot.kt.priceChecker.repo.users.UserRepository;
import dot.kt.priceChecker.service.sheets.SheetReader;
import dot.kt.priceChecker.support.TestTokens;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogApiTest {
    private static final String URL = "https://docs.google.com/spreadsheets/d/catalog-sheet/edit#gid=0";
    private static final String PASSWORD = "a-valid-catalog-password";
    private static final List<Object> HEADERS = row("sku", "name", "model", "price", "currency", "quantity",
            "condition", "category", "brand", "series", "appliance_type", "size", "features");

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired ItemRepository items;
    @Autowired SellerRepository sellers;
    @Autowired UserRepository users;
    @Autowired InventoryItemRepository inventory;
    @Autowired SheetImportRepository imports;
    @Autowired PasswordEncoder passwords;
    @MockitoBean SheetReader reader;

    @BeforeEach
    void clearDatabase() {
        imports.deleteAllInBatch();
        inventory.deleteAllInBatch();
        sellers.deleteAllInBatch();
        users.deleteAllInBatch();
        items.deleteAllInBatch();
    }

    @Test
    void inheritsPhoneAndApplianceFieldsAndScopesEachSuggestionStepToItsDomain() throws Exception {
        Seller seller = register("shop@example.com");
        save(seller, HEADERS,
                phone("S24", "Galaxy S24 Ultra", "S", "1000000", "NGN", "2", "Mint"),
                phone("A55", "Galaxy A55", "A", "450000", "NGN", "5", "Brand new"),
                row("IP13", "iPhone 13 Pro", "A2638/128GB", "600000", "NGN", "1", "Used", "PHONE", "iPhone", "iPhone 13", "", "", ""),
                row("TV55", "Samsung 55-inch TV", "TV55", "400000", "NGN", "2", "Brand new", "ELECTRICAL_APPLIANCE", "Samsung", "", "Television", "55 inch", "Smart TV;4K"),
                row("WM8", "LG washing machine", "F2", "350000", "NGN", "1", "Brand new", "Electrical appliances", "Life is good", "", "Washing machine", "8 kg", "Inverter;Front load"));
        var token = bearer(seller.getEmail());
        assertThat(items.count()).isEqualTo(5);
        assertThat(items.findAll()).filteredOn(item -> item.getName().equals("Galaxy S24 Ultra"))
                .singleElement().isInstanceOf(Gadget.class);
        assertThat(items.findAll()).filteredOn(item -> item.getName().equals("LG washing machine"))
                .singleElement().isInstanceOf(ElectricalAppliance.class);

        mvc.perform(get("/api/v1/catalog/domains").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].domain", containsInAnyOrder("GADGET", "ELECTRICAL_APPLIANCE")));
        mvc.perform(get("/api/v1/catalog/GADGET/brands").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInAnyOrder("Samsung", "Apple")));
        mvc.perform(get("/api/v1/catalog/ELECTRICAL_APPLIANCE/brands").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$", containsInAnyOrder("Samsung", "LG")));
        mvc.perform(get("/api/v1/catalog/GADGET/options").param("brand", "Samsung").with(token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.series", containsInAnyOrder("A", "S")))
                .andExpect(jsonPath("$.applianceTypes").isEmpty());
        mvc.perform(get("/api/v1/catalog/ELECTRICAL_APPLIANCE/options").param("brand", "life is good").with(token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.brand").value("LG"))
                .andExpect(jsonPath("$.series").isEmpty())
                .andExpect(jsonPath("$.applianceTypes[0]").value("Washing machine"))
                .andExpect(jsonPath("$.sizes[0]").value("8 kg"))
                .andExpect(jsonPath("$.features", containsInAnyOrder("Inverter", "Front load")));
        mvc.perform(get("/api/v1/catalog/GADGET/items").param("gadgetType", "PHONE").param("brand", "samsung").param("series", "s").with(token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Galaxy S24 Ultra"))
                .andExpect(jsonPath("$.items[0].category").value("GADGET"));
        mvc.perform(get("/api/v1/catalog/GADGET/items").param("gadgetType", "PHONE").param("brand", "Samsung").param("series", "S")
                        .param("q", "s24").with(token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/catalog/GADGET/items").param("gadgetType", "PHONE").param("brand", "Samsung").param("series", "S")
                        .param("q", "%").with(token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/catalog/ELECTRICAL_APPLIANCE/items").param("brand", "LG")
                        .param("applianceType", "washing machine").param("applianceSize", "8 kg").param("feature", "inverter").with(token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].model").value("F2"));

        UUID phoneId = catalogId(seller, "S24");
        mvc.perform(get("/api/v1/catalog/GADGET/items/" + phoneId).with(token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.series").value("S"));
        mvc.perform(get("/api/v1/catalog/ELECTRICAL_APPLIANCE/items/" + phoneId).with(token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/ELECTRICAL_APPLIANCE/items/" + phoneId + "/offers").with(token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/seller/inventory").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.sellerSku == 'WM8')].item.applianceType", containsInAnyOrder("Washing machine")));
    }

    @Test
    void requiresDomainBrandAndPhoneSeriesAndRejectsCrossDomainFilters() throws Exception {
        var token = buyer();
        mvc.perform(get("/api/v1/catalog/items").with(token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalog/TOY/brands").with(token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/GADGET/items").param("gadgetType", "PHONE").param("series", "S").with(token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/GADGET/items").param("gadgetType", "PHONE").param("brand", "Samsung").with(token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SERIES_REQUIRED"));
        mvc.perform(get("/api/v1/catalog/GADGET/items").param("gadgetType", "PHONE").param("brand", "Samsung").param("series", "S")
                        .param("applianceType", "Television").with(token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DOMAIN_FILTER"));
        mvc.perform(get("/api/v1/catalog/ELECTRICAL_APPLIANCE/items").param("brand", "Samsung")
                        .param("series", "S").with(token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DOMAIN_FILTER"));
        mvc.perform(get("/api/v1/catalog/GADGET/options").param("brand", " ").with(token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sharesCatalogIdentityAcrossSellersAndComparesOnlyAvailableOffersWithinOneCurrency() throws Exception {
        Seller first = register("first@example.com");
        Seller second = register("second@example.com");
        Seller dollar = register("usd@example.com");
        Seller empty = register("empty@example.com");
        save(first, HEADERS, phone("PHONE", "Galaxy S24 Ultra", "S", "550000", "NGN", "2", "Used"));
        save(second, HEADERS, phone("OTHER-SKU", "galaxy s24 ultra", "s", "500000", "NGN", "", "Mint"));
        save(dollar, HEADERS, phone("PHONE", "Galaxy S24 Ultra", "S", "300", "USD", "1", "Brand new"));
        save(empty, HEADERS, phone("PHONE", "Galaxy S24 Ultra", "S", "100", "NGN", "0", "Used"));
        assertThat(items.count()).isEqualTo(1);
        UUID id = catalogId(first, "PHONE");
        assertThat(catalogId(second, "OTHER-SKU")).isEqualTo(id);
        String offers = "/api/v1/catalog/GADGET/items/" + id + "/offers";
        var token = buyer();
        mvc.perform(get(offers).with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].priceMinor").value(50000000))
                .andExpect(jsonPath("$.items[1].priceMinor").value(55000000))
                .andExpect(jsonPath("$.items[0].sellerName").value("Shop"))
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].googleSheetUrl").doesNotExist());
        mvc.perform(get(offers).param("currency", "USD").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].priceMinor").value(30000));
        mvc.perform(get(offers).param("condition", "used").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        AppUser user = users.findByEmail(first.getEmail()).orElseThrow();
        user.setEnabled(false);
        users.saveAndFlush(user);
        mvc.perform(get(offers).with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(offers).param("currency", "INVALID").with(token)).andExpect(status().isBadRequest());
    }

    @Test
    void deletingOfferRemovesSuggestionsWithoutDeletingSharedItemOrChangingSheet() throws Exception {
        Seller seller = register("seller@example.com");
        save(seller, HEADERS, phone("PHONE", "Galaxy S24 Ultra", "S", "500000", "NGN", "1", "Used"));
        UUID itemId = catalogId(seller, "PHONE");
        UUID listingId = inventory.findAllBySellerId(seller.getId()).getFirst().getId();
        var token = bearer(seller.getEmail());
        mvc.perform(delete("/api/v1/seller/inventory/" + listingId).with(token)).andExpect(status().isNoContent());
        assertThat(items.existsById(itemId)).isTrue();
        mvc.perform(get("/api/v1/catalog/GADGET/brands").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/catalog/GADGET/items/" + itemId + "/offers").with(token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/seller/me/sheet").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[1][0]").value("PHONE"));
        save(seller, HEADERS, phone("PHONE", "Galaxy S24 Ultra", "S", "500000", "NGN", "1", "Used"));
        assertThat(catalogId(seller, "PHONE")).isEqualTo(itemId);
    }

    @ParameterizedTest
    @CsvSource({"PHONE,'',S,brand", "PHONE,Samsung,'',series", "ELECTRICAL_APPLIANCE,Hisense,'',appliance_type", "TOY,Samsung,S,category"})
    void invalidClassificationBlocksImportWithRowSpecificErrors(String category, String brand, String series, String field) throws Exception {
        Seller seller = register("seller@example.com");
        when(reader.read(URL)).thenReturn(new SheetSnapshot(URL, "Inventory", List.of(HEADERS,
                row("SKU", "Product", "Model", "100", "NGN", "1", "Used", category, brand, series, "", "", ""))));
        mvc.perform(put("/api/v1/seller/me/sheet").with(bearer(seller.getEmail())).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SHEET_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(2)).andExpect(jsonPath("$.errors[0].field").value(field));
        assertThat(items.count()).isZero();
        assertThat(inventory.count()).isZero();
        assertThat(sellers.findById(seller.getId()).orElseThrow().getGoogleSheetUrl()).isNull();
    }

    @Test
    void previewDoesNotCreateCatalogItemsUntilConfirmedAndLegacySheetsRemainValid() throws Exception {
        Seller seller = register("seller@example.com");
        var token = bearer(seller.getEmail());
        when(reader.read(URL)).thenReturn(new SheetSnapshot(URL, "Inventory", List.of(HEADERS,
                phone("PHONE", "Galaxy S24 Ultra", "S", "500000", "NGN", "1", "Used"))));
        var preview = mvc.perform(post("/api/v1/seller/inventory/imports").with(token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.preview[0].catalog.category").value("GADGET")).andReturn();
        assertThat(items.count()).isZero();
        String id = body(preview).path("id").asText();
        mvc.perform(post("/api/v1/seller/inventory/imports/" + id + "/confirm").with(token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isOk());
        UUID catalogId = catalogId(seller, "PHONE");
        save(seller, row("sku", "name", "price", "brand"), row("PHONE", "Updated phone", "550000", "Samsung"));
        assertThat(catalogId(seller, "PHONE")).isEqualTo(catalogId);
        save(seller, row("sku", "name", "price"), row("LEGACY", "Cable", "1000"));
        assertThat(inventory.findAllBySellerId(seller.getId())).filteredOn(item -> item.getSellerSku().equals("LEGACY"))
                .singleElement().satisfies(item -> assertThat(item.getItem()).isNull());
    }

    @Test
    void adminCanCreateItemsAndOwnersCanClassifyExistingRowsWithoutChangingSheet() throws Exception {
        Seller seller = register("seller@example.com");
        Seller other = register("other@example.com");
        seed("admin@example.com", Role.ADMIN);
        seed("manager@example.com", Role.MANAGER);
        String definition = json.writeValueAsString(Map.of("category", "GADGET", "gadgetType", "PHONE", "brand", "Samsung", "series", "A", "name", "Galaxy A55"));
        mvc.perform(post("/api/v1/management/items").with(bearer(seller.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(definition)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/management/items").with(bearer("manager@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(definition)).andExpect(status().isForbidden());
        var created = mvc.perform(post("/api/v1/management/items").with(bearer("admin@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(definition))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.series").value("A")).andReturn();
        String itemId = body(created).path("id").asText();
        save(seller, row("sku", "name", "price"), row("OLD", "My phone", "100"));
        UUID listingId = inventory.findAllBySellerId(seller.getId()).getFirst().getId();
        var token = bearer(seller.getEmail());
        var preview = mvc.perform(post("/api/v1/seller/inventory/imports/saved").with(token))
                .andExpect(status().isCreated()).andReturn();
        String path = "/api/v1/seller/inventory/" + listingId + "/item";
        String assignment = json.writeValueAsString(Map.of("itemId", itemId));
        mvc.perform(put(path).with(bearer(other.getEmail())).contentType(MediaType.APPLICATION_JSON).content(assignment))
                .andExpect(status().isNotFound());
        mvc.perform(put(path).with(token).contentType(MediaType.APPLICATION_JSON).content(assignment))
                .andExpect(status().isOk()).andExpect(jsonPath("$.item.id").value(itemId));
        mvc.perform(post("/api/v1/seller/inventory/imports/" + body(preview).path("id").asText() + "/confirm").with(token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_STALE"));
        mvc.perform(get("/api/v1/catalog/GADGET/items").param("gadgetType", "PHONE").param("brand", "Samsung").param("series", "A").with(token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/catalog/GADGET/brands")).andExpect(status().isOk());
    }

    private Seller register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/sellers/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD, "displayName", "Shop"))))
                .andExpect(status().isCreated());
        return sellers.findByEmail(email).orElseThrow();
    }

    private void seed(String email, Role role) { users.saveAndFlush(new AppUser(email, passwords.encode(PASSWORD), role)); }
    private RequestPostProcessor bearer(String email) throws Exception { return TestTokens.bearer(mvc, json, email, PASSWORD); }
    private RequestPostProcessor buyer() throws Exception { seed("buyer@example.com", Role.USER); return bearer("buyer@example.com"); }

    @SafeVarargs
    private void save(Seller seller, List<Object>... rows) throws Exception {
        when(reader.read(URL)).thenReturn(new SheetSnapshot(URL, "Inventory", List.of(rows)));
        mvc.perform(put("/api/v1/seller/me/sheet").with(bearer(seller.getEmail())).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("url", URL))))
                .andExpect(status().isOk());
    }

    private UUID catalogId(Seller seller, String sku) {
        return inventory.findAllBySellerId(seller.getId()).stream().filter(item -> item.getSellerSku().equals(sku))
                .findFirst().orElseThrow().getItem().getId();
    }

    private static List<Object> phone(String sku, String name, String series, String price, String currency, String quantity, String condition) {
        return row(sku, name, name, price, currency, quantity, condition, "PHONE", "Samsung", series, "", "", "");
    }

    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private static List<Object> row(Object... cells) { return Arrays.asList(cells); }
}
