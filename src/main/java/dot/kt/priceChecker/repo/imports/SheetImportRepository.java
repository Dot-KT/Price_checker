package dot.kt.priceChecker.repo.imports;

import dot.kt.priceChecker.entities.imports.SheetImport;
import dot.kt.priceChecker.enums.imports.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SheetImportRepository extends JpaRepository<SheetImport, UUID> {
    Page<SheetImport> findAllBySellerId(UUID sellerId, Pageable pageable);

    Optional<SheetImport> findByIdAndSellerId(UUID id, UUID sellerId);

    boolean existsBySellerIdAndStatusAndCompletedAtAfter(UUID sellerId, ImportStatus status,
                                                        Instant completedAt);
}
