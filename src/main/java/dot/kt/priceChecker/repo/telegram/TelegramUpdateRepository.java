package dot.kt.priceChecker.repo.telegram;

import dot.kt.priceChecker.entities.telegram.TelegramUpdateReceipt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TelegramUpdateRepository extends JpaRepository<TelegramUpdateReceipt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from TelegramUpdateReceipt u where u.updateId = :id")
    Optional<TelegramUpdateReceipt> lock(@Param("id") long id);
    @Query("select coalesce(max(u.updateId), -1) from TelegramUpdateReceipt u")
    long latestUpdateId();
    @Query("select max(u.receivedAt) from TelegramUpdateReceipt u")
    Instant lastReceivedAt();
    @Query("select u from TelegramUpdateReceipt u where (u.leaseUntil is null or u.leaseUntil <= :now) and (u.processedAt is null or u.repliesJson is not null) and not exists (select earlier.updateId from TelegramUpdateReceipt earlier where earlier.chatId = u.chatId and earlier.updateId < u.updateId and (earlier.processedAt is null or earlier.repliesJson is not null)) order by u.updateId")
    List<TelegramUpdateReceipt> pending(@Param("now") Instant now, Pageable page);
}
