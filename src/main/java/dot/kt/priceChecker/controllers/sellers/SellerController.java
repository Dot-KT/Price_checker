package dot.kt.priceChecker.controllers.sellers;

import dot.kt.priceChecker.dto.sellers.SaveSellerSheetRequest;
import dot.kt.priceChecker.dto.sellers.SellerResponse;
import dot.kt.priceChecker.dto.sellers.UpdateSellerRequest;
import dot.kt.priceChecker.service.sellers.SellerService;
import dot.kt.priceChecker.service.sheets.SellerSheetService;
import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Sellers", description = "Your seller profile and saved Google Sheets link")
public class SellerController {
    private final SellerService sellers;
    private final SellerSheetService sheets;

    public SellerController(SellerService sellers, SellerSheetService sheets) {
        this.sellers = sellers;
        this.sheets = sheets;
    }

    @GetMapping("/seller/me")
    @Operation(summary = "Get your seller profile")
    public SellerResponse current() {
        return sellers.current();
    }

    @PutMapping(value = "/seller/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update your seller profile")
    public SellerResponse update(@Valid @RequestBody UpdateSellerRequest request) {
        return sellers.update(request);
    }

    @DeleteMapping("/seller/me")
    @Operation(summary = "Delete your seller account", description = "Permanently deletes the account, saved sheet link, inventory, and import history.")
    @ApiResponse(responseCode = "204", description = "Seller account deleted")
    public ResponseEntity<Void> delete() {
        sellers.delete();
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/seller/me/sheet", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Save a public sheet and import its inventory", description = "Downloads and validates the sheet, then saves the link and imports all products atomically. Only Viewer access is needed; Google Sheets is never modified. Invalid rows return 422 with row errors and leave the previous link and inventory unchanged.")
    public SellerResponse saveSheet(@Valid @RequestBody SaveSellerSheetRequest request) {
        return sheets.save(request.url());
    }

    @GetMapping("/seller/me/sheet")
    @Operation(summary = "Read your saved public spreadsheet", description = "Loads the link from your seller account and returns its current rows. Does not change inventory.")
    public SheetSnapshot readSheet() { return sheets.read(); }

    @DeleteMapping("/seller/me/sheet")
    @Operation(summary = "Remove your saved sheet link", description = "Leaves existing inventory unchanged.")
    @ApiResponse(responseCode = "204", description = "Saved sheet link removed")
    public ResponseEntity<Void> removeSheet() {
        sellers.removeSheet();
        return ResponseEntity.noContent().build();
    }
}
