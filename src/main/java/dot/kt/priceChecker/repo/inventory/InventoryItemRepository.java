package dot.kt.priceChecker.repo.inventory;

import dot.kt.priceChecker.entities.inventory.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID>, JpaSpecificationExecutor<InventoryItem> {

    List<InventoryItem> findAllBySellerId(UUID sellerId);

    Optional<InventoryItem> findByIdAndSellerId(UUID id, UUID sellerId);

    Page<InventoryItem> findAllBySellerIdAndActiveTrue(UUID sellerId, Pageable pageable);
}
