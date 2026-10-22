package dot.kt.priceChecker.service.inventory;

import dot.kt.priceChecker.dto.inventory.InventoryItemResponse;
import dot.kt.priceChecker.dto.inventory.InventoryPage;
import dot.kt.priceChecker.entities.inventory.InventoryItem;
import dot.kt.priceChecker.exceptions.common.ApiException;
import dot.kt.priceChecker.repo.inventory.InventoryItemRepository;
import dot.kt.priceChecker.repo.sellers.SellerRepository;
import dot.kt.priceChecker.repo.catalog.ItemRepository;
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
    private final SellerRepository sellerRepository;
    private final ItemRepository items;

    public InventoryService(SellerService sellers, InventoryItemRepository inventory, CurrentUserService currentUser,
            SellerRepository sellerRepository, ItemRepository items) {
        this.sellers = sellers;
        this.inventory = inventory;
        this.currentUser = currentUser;
        this.sellerRepository = sellerRepository;
        this.items = items;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('seller:read')")
    public InventoryPage inventory(PageQuery query) {
        return inventory(currentUser.id(), query);
    }

    @Transactional
    @PreAuthorize("hasAuthority('seller:write')")
    public void delete(UUID id) {
        UUID sellerId = currentUser.id();
        // Use the same seller lock as imports so a concurrent replacement cannot undo this deletion.
        var seller = sellerRepository.findByIdForUpdate(sellerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "Seller account not found."));
        var item = inventory.findByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Inventory item not found."));
        inventory.delete(item);
        seller.advanceInventoryRevision();
        inventory.flush();
    }

    @Transactional
    @PreAuthorize("hasAuthority('seller:write')")
    public InventoryItemResponse assignItem(UUID id, UUID itemId) {
        return assignItem(currentUser.id(), id, itemId);
    }

    @Transactional
    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public InventoryItemResponse assignItem(UUID sellerId, UUID id, UUID itemId) {
        var seller = sellerRepository.findByIdForUpdate(sellerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "Seller account not found."));
        var listing = inventory.findByIdAndSellerId(id, sellerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Inventory item not found."));
        var item = items.findById(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Catalog item not found."));
        listing.assignItem(item);
        seller.advanceInventoryRevision();
        inventory.flush();
        return InventoryItemResponse.from(listing);
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
