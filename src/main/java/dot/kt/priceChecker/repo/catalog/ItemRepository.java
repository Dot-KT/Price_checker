package dot.kt.priceChecker.repo.catalog;

import dot.kt.priceChecker.entities.catalog.Item;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {
    Optional<Item> findByIdentityKey(String identityKey);
}
