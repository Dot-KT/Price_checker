package dot.kt.priceChecker.service.imports;

import dot.kt.priceChecker.dto.imports.ImportResponse;
import dot.kt.priceChecker.dto.sheets.ParsedInventoryItem;
import dot.kt.priceChecker.dto.sheets.SheetParseResult;
import dot.kt.priceChecker.dto.sheets.SheetRowError;
import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import dot.kt.priceChecker.entities.imports.SheetImport;
import dot.kt.priceChecker.entities.inventory.InventoryItem;
import dot.kt.priceChecker.entities.sellers.Seller;
import dot.kt.priceChecker.enums.imports.ImportStatus;
import dot.kt.priceChecker.exceptions.common.ApiException;
import dot.kt.priceChecker.repo.imports.SheetImportRepository;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.repo.sellers.SellerRepository;
import dot.kt.priceChecker.service.sellers.SellerService;
import dot.kt.priceChecker.service.security.CurrentUserService;
import dot.kt.priceChecker.dto.common.PageQuery;
import dot.kt.priceChecker.dto.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import dot.kt.priceChecker.service.sheets.SheetParser;
import dot.kt.priceChecker.service.sheets.SheetReader;
import dot.kt.priceChecker.service.sheets.SellerSheetService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SheetImportService {
    private static final int PREVIEW_SIZE = 20;
    private static final TypeReference<List<ParsedInventoryItem>> ITEMS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<SheetRowError>> ERRORS_TYPE = new TypeReference<>() {};

    private final SellerService sellers;
    private final SellerRepository sellerRepository;
    private final SheetReader reader;
    private final SheetParser parser;
    private final SheetImportRepository imports;
    private final InventoryItemRepository inventory;
    private final JsonMapper json;
    private final CurrentUserService currentUser;
    private final SellerSheetService sheets;

    public SheetImportService(SellerService sellers, SellerRepository sellerRepository,
            SheetReader reader, SheetParser parser, SheetImportRepository imports,
            InventoryItemRepository inventory, JsonMapper json, CurrentUserService currentUser, SellerSheetService sheets) {
        this.sellers = sellers;
        this.sellerRepository = sellerRepository;
        this.reader = reader;
        this.parser = parser;
        this.imports = imports;
        this.inventory = inventory;
        this.json = json;
        this.currentUser = currentUser;
        this.sheets = sheets;
    }

    // Reading Google happens before persistence, without holding a database transaction/lock.
    @PreAuthorize("hasAuthority('seller:write')")
    public ImportResponse preview(String url) { return preview(currentUser.id(), url); }

    @PreAuthorize("hasAuthority('seller:write')")
    public ImportResponse previewSaved() { return previewSaved(currentUser.id()); }

    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public ImportResponse previewSaved(UUID sellerId) { return preview(sellerId, sheets.savedLink(sellerId)); }

    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public ImportResponse preview(UUID sellerId, String url) {
        Seller seller = sellers.requireSeller(sellerId);
        SheetSnapshot snapshot = reader.read(url);
        SheetParseResult result = parser.parse(snapshot.rows(), seller.getDefaultCurrency());
        int errorRows = result.totalRows() - result.items().size();
        SheetImport sheetImport = new SheetImport(seller, snapshot.sourceUrl(), snapshot.sheetTitle(),
                result.totalRows(), result.items().size(), errorRows,
                json.writeValueAsString(result.items()), json.writeValueAsString(result.errors()));
        if (result.hasErrors()) {
            sheetImport.invalidate();
        }
        return response(imports.saveAndFlush(sheetImport));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('seller:read')")
    public ImportResponse get(UUID id) { return get(currentUser.id(), id); }

    @Transactional(readOnly = true)
    @PreAuthorize("@sellerAccess.canRead(#sellerId)")
    public ImportResponse get(UUID sellerId, UUID id) {
        Seller seller = sellers.requireSeller(sellerId);
        return response(ownedImport(id, seller.getId()));
    }

    @Transactional
    @PreAuthorize("hasAuthority('seller:write')")
    public ImportResponse confirm(UUID id) { return confirm(currentUser.id(), id); }

    @Transactional
    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public ImportResponse confirm(UUID sellerId, UUID id) {
        Seller account = sellers.requireSeller(sellerId);
        // Serialize replacements for this seller, including repeated/concurrent confirmations.
        Seller seller = sellerRepository.findByIdForUpdate(account.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SELLER_NOT_FOUND", "Seller account not found."));
        SheetImport sheetImport = ownedImport(id, seller.getId());
        if (sheetImport.getStatus() == ImportStatus.COMPLETED) {
            return response(sheetImport);
        }
        if (sheetImport.getStatus() == ImportStatus.INVALID) {
            throw new ApiException(HttpStatus.CONFLICT, "IMPORT_INVALID",
                    "Fix the sheet errors and submit a new preview before confirming.");
        }
        if (imports.existsBySellerIdAndStatusAndCompletedAtAfter(
                seller.getId(), ImportStatus.COMPLETED, sheetImport.getCreatedAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "IMPORT_STALE",
                    "Inventory changed after this preview. Submit the sheet again to create a fresh preview.");
        }

        List<ParsedInventoryItem> rows = json.readValue(sheetImport.getItemsJson(), ITEMS_TYPE);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "IMPORT_EMPTY", "An empty import cannot replace inventory.");
        }
        Map<String, InventoryItem> existing = new HashMap<>();
        for (InventoryItem item : inventory.findAllBySellerId(seller.getId())) {
            existing.put(item.getSellerSku(), item);
        }
        for (ParsedInventoryItem row : rows) {
            InventoryItem item = existing.remove(row.sellerSku());
            if (item == null) {
                inventory.save(new InventoryItem(seller, row.sellerSku(), row.name(), row.model(),
                        row.description(), row.condition(), row.priceMinor(), row.currency(), row.quantity(), row.sourceRow()));
            } else {
                item.update(row.name(), row.model(), row.description(), row.condition(), row.priceMinor(),
                        row.currency(), row.quantity(), row.sourceRow());
            }
        }
        existing.values().stream().filter(InventoryItem::isActive).forEach(InventoryItem::deactivate);
        sheetImport.complete();
        inventory.flush();
        imports.flush();
        return response(sheetImport);
    }

    private SheetImport ownedImport(UUID id, UUID sellerId) {
        return imports.findByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "Import not found."));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@sellerAccess.canRead(#sellerId)")
    public PageResponse<ImportResponse> list(UUID sellerId, PageQuery query) {
        sellers.requireSeller(sellerId);
        return PageResponse.from(imports.findAllBySellerId(sellerId,
                PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(this::response));
    }

    private ImportResponse response(SheetImport sheetImport) {
        List<ParsedInventoryItem> items = json.readValue(sheetImport.getItemsJson(), ITEMS_TYPE);
        List<SheetRowError> errors = json.readValue(sheetImport.getErrorsJson(), ERRORS_TYPE);
        return new ImportResponse(sheetImport.getId(), sheetImport.getStatus(), sheetImport.getSourceUrl(),
                sheetImport.getSheetTitle(), sheetImport.getTotalRows(), sheetImport.getValidRows(),
                sheetImport.getErrorRows(), items.stream().limit(PREVIEW_SIZE).toList(), errors,
                sheetImport.getCreatedAt(), sheetImport.getCompletedAt());
    }
}
