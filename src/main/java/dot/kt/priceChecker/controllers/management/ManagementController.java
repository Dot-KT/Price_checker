package dot.kt.priceChecker.controllers.management;

import dot.kt.priceChecker.dto.common.PageQuery;
import dot.kt.priceChecker.dto.common.PageResponse;
import dot.kt.priceChecker.dto.imports.ConfirmImportRequest;
import dot.kt.priceChecker.dto.imports.ImportResponse;
import dot.kt.priceChecker.dto.imports.ImportSheetRequest;
import dot.kt.priceChecker.dto.imports.SellerImportTarget;
import dot.kt.priceChecker.dto.inventory.InventoryPage;
import dot.kt.priceChecker.dto.inventory.InventoryItemResponse;
import dot.kt.priceChecker.dto.inventory.SellerInventoryItemTarget;
import dot.kt.priceChecker.dto.catalog.AssignItemRequest;
import dot.kt.priceChecker.dto.sellers.SaveSellerSheetRequest;
import dot.kt.priceChecker.dto.sellers.SellerResponse;
import dot.kt.priceChecker.dto.sellers.SellerTarget;
import dot.kt.priceChecker.dto.sellers.UpdateSellerRequest;
import dot.kt.priceChecker.dto.users.AccountResponse;
import dot.kt.priceChecker.service.imports.SheetImportService;
import dot.kt.priceChecker.service.inventory.InventoryService;
import dot.kt.priceChecker.service.security.AccountService;
import dot.kt.priceChecker.service.sellers.SellerService;
import dot.kt.priceChecker.service.sheets.SellerSheetService;
import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management")
@Tag(name = "Management", description = "ADMIN can read and write across accounts; MANAGER can only read")
public class ManagementController {
    private final AccountService accounts;
    private final SellerService sellers;
    private final InventoryService inventory;
    private final SheetImportService imports;
    private final SellerSheetService sheets;

    public ManagementController(AccountService accounts, SellerService sellers,
            InventoryService inventory, SheetImportService imports, SellerSheetService sheets) {
        this.accounts = accounts;
        this.sellers = sellers;
        this.inventory = inventory;
        this.imports = imports;
        this.sheets = sheets;
    }

    @GetMapping("/accounts")
    @Operation(summary = "List shared user accounts", description = "ADMIN or MANAGER. Password hashes are never included.")
    public PageResponse<AccountResponse> accounts(@Valid @ParameterObject @ModelAttribute PageQuery query) {
        return accounts.list(query);
    }

    @GetMapping("/sellers")
    @Operation(summary = "List sellers", description = "ADMIN or MANAGER")
    public PageResponse<SellerResponse> sellers(@Valid @ParameterObject @ModelAttribute PageQuery query) {
        return sellers.list(query);
    }

    @GetMapping("/sellers/{sellerId}")
    @Operation(summary = "Read a seller profile", description = "ADMIN or MANAGER")
    public SellerResponse seller(@Valid @ParameterObject @ModelAttribute SellerTarget target) {
        return sellers.get(target.sellerId());
    }

    @PutMapping(value = "/sellers/{sellerId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a seller profile", description = "ADMIN only")
    public SellerResponse update(@Valid @ParameterObject @ModelAttribute SellerTarget target,
            @Valid @RequestBody UpdateSellerRequest request) {
        return sellers.update(target.sellerId(), request);
    }

    @DeleteMapping("/sellers/{sellerId}")
    @Operation(summary = "Delete a seller account and its data", description = "ADMIN only. Permanently removes the account, inventory, and import history.")
    @ApiResponse(responseCode = "204", description = "Seller account deleted")
    public ResponseEntity<Void> delete(@Valid @ParameterObject @ModelAttribute SellerTarget target) {
        sellers.delete(target.sellerId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/sellers/{sellerId}/sheet", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Save a seller's public sheet and import inventory", description = "ADMIN only. Reads and validates the sheet, then saves the link and imports products atomically. The source sheet is never modified.")
    public SellerResponse saveSheet(@Valid @ParameterObject @ModelAttribute SellerTarget target,
            @Valid @RequestBody SaveSellerSheetRequest request) {
        return sheets.save(target.sellerId(), request.url());
    }

    @GetMapping("/sellers/{sellerId}/sheet")
    @Operation(summary = "Read a seller's saved public spreadsheet", description = "ADMIN or MANAGER. Returns current sheet rows without changing inventory.")
    public SheetSnapshot readSheet(@Valid @ParameterObject @ModelAttribute SellerTarget target) {
        return sheets.read(target.sellerId());
    }

    @DeleteMapping("/sellers/{sellerId}/sheet")
    @Operation(summary = "Remove a seller's sheet link", description = "ADMIN only; leaves inventory unchanged")
    @ApiResponse(responseCode = "204", description = "Sheet link removed")
    public ResponseEntity<Void> removeSheet(@Valid @ParameterObject @ModelAttribute SellerTarget target) {
        sellers.removeSheet(target.sellerId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sellers/{sellerId}/inventory")
    @Operation(summary = "Read a seller's inventory", description = "ADMIN or MANAGER")
    public InventoryPage inventory(@Valid @ParameterObject @ModelAttribute SellerTarget target,
            @Valid @ParameterObject @ModelAttribute PageQuery query) {
        return inventory.inventory(target.sellerId(), query);
    }

    @PutMapping(value = "/sellers/{sellerId}/inventory/{id}/item", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Classify a seller's inventory listing", description = "ADMIN only. Links an existing listing to a shared catalog item without changing Google Sheets.")
    public InventoryItemResponse assignItem(@Valid @ParameterObject @ModelAttribute SellerInventoryItemTarget target,
            @Valid @RequestBody AssignItemRequest request) {
        return inventory.assignItem(target.sellerId(), target.id(), request.itemId());
    }

    @GetMapping("/sellers/{sellerId}/inventory/imports")
    @Operation(summary = "List a seller's import history", description = "ADMIN or MANAGER")
    public PageResponse<ImportResponse> imports(@Valid @ParameterObject @ModelAttribute SellerTarget target,
            @Valid @ParameterObject @ModelAttribute PageQuery query) {
        return imports.list(target.sellerId(), query);
    }

    @GetMapping("/sellers/{sellerId}/inventory/imports/{id}")
    @Operation(summary = "Read a seller's import preview or result", description = "ADMIN or MANAGER")
    public ImportResponse getImport(@Valid @ParameterObject @ModelAttribute SellerImportTarget target) {
        return imports.get(target.sellerId(), target.id());
    }

    @PostMapping(value = "/sellers/{sellerId}/inventory/imports", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview an import for a seller", description = "ADMIN only; creates an import record")
    @ApiResponse(responseCode = "201", description = "Import preview created")
    public ResponseEntity<ImportResponse> preview(@Valid @ParameterObject @ModelAttribute SellerTarget target,
            @Valid @RequestBody ImportSheetRequest request) {
        var response = imports.preview(target.sellerId(), request.url());
        return ResponseEntity.created(URI.create("/api/v1/management/sellers/" + target.sellerId()
                + "/inventory/imports/" + response.id())).body(response);
    }

    @PostMapping("/sellers/{sellerId}/inventory/imports/saved")
    @Operation(summary = "Preview a seller's saved sheet", description = "ADMIN only. Reads the URL stored on that seller's account; no request body is needed.")
    @ApiResponse(responseCode = "201", description = "Import preview created")
    public ResponseEntity<ImportResponse> previewSaved(@Valid @ParameterObject @ModelAttribute SellerTarget target) {
        var response = imports.previewSaved(target.sellerId());
        return ResponseEntity.created(URI.create("/api/v1/management/sellers/" + target.sellerId()
                + "/inventory/imports/" + response.id())).body(response);
    }

    @PostMapping(value = "/sellers/{sellerId}/inventory/imports/{id}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirm an import for a seller", description = "ADMIN only; replaces inventory using the saved preview")
    public ImportResponse confirm(@Valid @ParameterObject @ModelAttribute SellerImportTarget target,
            @Valid @RequestBody ConfirmImportRequest request) {
        return imports.confirm(target.sellerId(), target.id());
    }
}
