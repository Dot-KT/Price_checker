package dot.kt.priceChecker.service.inventory;

import dot.kt.priceChecker.dto.inventory.InventoryItemResponse;
import dot.kt.priceChecker.dto.inventory.InventoryPage;
import dot.kt.priceChecker.entities.inventory.InventoryItem;
import dot.kt.priceChecker.exceptions.common.ApiException;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.service.sellers.SellerService;
import dot.kt.priceChecker.service.security.CurrentUserService;
import dot.kt.priceChecker.dto.common.PageQuery;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final SellerService sellers;
    private final InventoryItemRepository inventory;
    private final CurrentUserService currentUser;

    public InventoryService(SellerService sellers, InventoryItemRepository inventory, CurrentUserService currentUser) {
        this.sellers = sellers;
        this.inventory = inventory;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('seller:read')")
    public InventoryPage inventory(PageQuery query) {
        return inventory(currentUser.id(), query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@sellerAccess.canRead(#sellerId)")
    public InventoryPage inventory(UUID sellerId, PageQuery query) {
        int page = query.page();
        int size = query.size();
        if (page < 0 || size < 1 || size > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page must be nonnegative and size between 1 and 200.");
        }
        Page<InventoryItem> items = inventory.findAllBySellerIdAndActiveTrue(
                sellers.requireSeller(sellerId).getId(), PageRequest.of(page, size, Sort.by("name", "id")));
        return new InventoryPage(items.map(InventoryItemResponse::from).getContent(),
                items.getNumber(), items.getSize(), items.getTotalElements(), items.getTotalPages());
    }
}
