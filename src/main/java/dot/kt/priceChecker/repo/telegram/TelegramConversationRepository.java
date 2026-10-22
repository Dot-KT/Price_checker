package dot.kt.priceChecker.repo.telegram;

import dot.kt.priceChecker.entities.telegram.TelegramConversation;
import dot.kt.priceChecker.enums.telegram.ConversationPhase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TelegramConversationRepository extends JpaRepository<TelegramConversation, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TelegramConversation c where c.user.id = :userId and c.endedAt is null order by c.createdAt desc")
    List<TelegramConversation> active(@Param("userId") UUID userId);
    Page<TelegramConversation> findAllByUserTelegramIdOrderByCreatedAtDesc(long telegramId, Pageable page);
    @Modifying
    @Query("update TelegramConversation c set c.phase = :phase, c.endedAt = :now, c.pendingUpdateId = null, c.revision = c.revision + 1 where c.endedAt is null and c.lastActivityAt <= :cutoff")
    int expire(@Param("cutoff") Instant cutoff, @Param("now") Instant now, @Param("phase") ConversationPhase phase);
}
