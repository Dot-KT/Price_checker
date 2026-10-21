package dot.kt.priceChecker.controllers.imports;

import dot.kt.priceChecker.dto.imports.ConfirmImportRequest;
import dot.kt.priceChecker.dto.imports.ImportResponse;
import dot.kt.priceChecker.dto.imports.ImportSheetRequest;
import dot.kt.priceChecker.dto.imports.ImportTarget;
import dot.kt.priceChecker.service.imports.SheetImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/inventory/imports")
@Tag(name = "Imports", description = "Preview Google Sheets inventory, then explicitly confirm the import")
public class SheetImportController {
    private final SheetImportService imports;

    public SheetImportController(SheetImportService imports) {
        this.imports = imports;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview an inventory import", description = "Reads and validates a publicly shared sheet without changing live inventory. No Google credentials are required.")
    @ApiResponse(responseCode = "201", description = "Import preview created; inspect its status and errors before confirming")
    public ResponseEntity<ImportResponse> preview(@Valid @RequestBody ImportSheetRequest request) {
        ImportResponse response = imports.preview(request.url());
        return ResponseEntity.created(URI.create("/api/v1/seller/inventory/imports/" + response.id()))
                .body(response);
    }

    @PostMapping("/saved")
    @Operation(summary = "Preview an import from your saved sheet link", description = "Loads the URL from your seller account. No request body is needed; confirmation is a separate step.")
    @ApiResponse(responseCode = "201", description = "Import preview created")
    public ResponseEntity<ImportResponse> previewSaved() {
        ImportResponse response = imports.previewSaved();
        return ResponseEntity.created(URI.create("/api/v1/seller/inventory/imports/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an import preview or result")
    public ImportResponse get(@Valid @ParameterObject @ModelAttribute ImportTarget target) {
        return imports.get(target.id());
    }

    @PostMapping(value = "/{id}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirm an inventory import", description = "Send confirm=true to apply the saved preview. Omitted products become inactive. Invalid or stale previews cannot be confirmed.")
    public ImportResponse confirm(@Valid @ParameterObject @ModelAttribute ImportTarget target,
            @Valid @RequestBody ConfirmImportRequest request) {
        return imports.confirm(target.id());
    }
}
