package dot.kt.priceChecker.repo.intel;

import dot.kt.priceChecker.entities.intel.PriceCheckerIntel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceIntelRepository extends JpaRepository<PriceCheckerIntel, UUID> {
    Optional<PriceCheckerIntel> findByItemIdAndCurrency(UUID itemId, String currency);
    void deleteByItemIdAndCurrency(UUID itemId, String currency);
}
