package dot.kt.priceChecker.controllers.catalog;

import dot.kt.priceChecker.dto.catalog.CreateItemRequest;
import dot.kt.priceChecker.dto.catalog.ItemResponse;
import dot.kt.priceChecker.service.catalog.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management/items")
@Tag(name = "Management")
public class CatalogManagementController {
    private final CatalogService catalog;

    public CatalogManagementController(CatalogService catalog) { this.catalog = catalog; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or reuse a shared catalog item", description = "ADMIN only. Creates a Gadget or ElectricalAppliance. Equivalent normalized definitions share one item ID; seller-specific prices and conditions stay in inventory.")
    @ApiResponse(responseCode = "201", description = "Catalog item created or reused")
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        ItemResponse item = catalog.create(request);
        return ResponseEntity.created(URI.create("/api/v1/catalog/" + item.category() + "/items/" + item.id())).body(item);
    }
}
