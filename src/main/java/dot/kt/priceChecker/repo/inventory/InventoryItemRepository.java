package dot.kt.priceChecker.repo.inventory;

import dot.kt.priceChecker.entities.inventory.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    List<InventoryItem> findAllBySellerId(UUID sellerId);

    Page<InventoryItem> findAllBySellerIdAndActiveTrue(UUID sellerId, Pageable pageable);
}
