package dot.kt.priceChecker.repo.sellers;

import dot.kt.priceChecker.entities.sellers.Seller;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SellerRepository extends JpaRepository<Seller, UUID> {

    @Query("select s from Seller s join fetch s.user u where u.email = :email")
    Optional<Seller> findByEmail(@Param("email") String email);

    @Query("select (count(s) > 0) from Seller s where s.user.email = :email")
    boolean existsByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seller s where s.id = :id")
    Optional<Seller> findByIdForUpdate(@Param("id") UUID id);
}
