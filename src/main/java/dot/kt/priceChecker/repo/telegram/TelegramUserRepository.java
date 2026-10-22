package dot.kt.priceChecker.repo.telegram;

import dot.kt.priceChecker.entities.telegram.TelegramUser;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TelegramUserRepository extends JpaRepository<TelegramUser, UUID>, JpaSpecificationExecutor<TelegramUser> {
    Optional<TelegramUser> findByTelegramId(long telegramId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from TelegramUser u where u.telegramId = :telegramId")
    Optional<TelegramUser> lockByTelegramId(@Param("telegramId") long telegramId);
}
