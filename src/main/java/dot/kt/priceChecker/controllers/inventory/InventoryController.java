package dot.kt.priceChecker.controllers.inventory;

import dot.kt.priceChecker.dto.inventory.InventoryPage;
import dot.kt.priceChecker.dto.inventory.InventoryItemTarget;
import dot.kt.priceChecker.dto.inventory.InventoryItemResponse;
import dot.kt.priceChecker.dto.catalog.AssignItemRequest;
import dot.kt.priceChecker.service.inventory.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import dot.kt.priceChecker.dto.common.PageQuery;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/inventory")
@Tag(name = "Inventory", description = "The authenticated seller's active inventory")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    @Operation(summary = "List your inventory", description = "Returns a page of active inventory. Page numbers start at zero; the maximum page size is 200.")
    public InventoryPage inventory(@Valid @ParameterObject @ModelAttribute PageQuery query) {
        return inventory.inventory(query);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an inventory item", description = "Deletes your item from the app database only. The Google Sheet is unchanged. A later explicit import can restore a product still present on the sheet.")
    @ApiResponse(responseCode = "204", description = "Inventory item deleted")
    public ResponseEntity<Void> delete(@Valid @ParameterObject @ModelAttribute InventoryItemTarget target) {
        inventory.delete(target.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{id}/item", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Classify an inventory listing", description = "Links your database listing to an existing phone or appliance catalog item. Google Sheets is unchanged.")
    public InventoryItemResponse assignItem(@Valid @ParameterObject @ModelAttribute InventoryItemTarget target,
            @Valid @RequestBody AssignItemRequest request) {
        return inventory.assignItem(target.id(), request.itemId());
    }
}
