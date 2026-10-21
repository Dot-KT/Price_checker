package dot.kt.priceChecker.controllers.inventory;

import dot.kt.priceChecker.dto.inventory.InventoryPage;
import dot.kt.priceChecker.service.inventory.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import dot.kt.priceChecker.dto.common.PageQuery;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
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
}
