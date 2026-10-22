package dot.kt.priceChecker.service.telegram;

import dot.kt.priceChecker.dto.common.*;
import dot.kt.priceChecker.dto.telegram.BotOffer;
import dot.kt.priceChecker.entities.telegram.*;
import dot.kt.priceChecker.repo.telegram.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAuthority('project:read')")
public class TelegramAdminService {
    private final TelegramUserRepository users;
    private final TelegramConversationRepository conversations;
    private final JsonMapper json;
    public TelegramAdminService(TelegramUserRepository users, TelegramConversationRepository conversations, JsonMapper json) {
        this.users = users; this.conversations = conversations; this.json = json;
    }
    public record Profile(long telegramId, String username, String firstName, String lastName, String realName,
            UUID selectedItemId, UUID selectedListingId, BotOffer selection, Instant updatedAt) { }
    public record Conversation(UUID id, String phase, String domain, String gadgetType, String question,
            BotOffer selection, Instant createdAt, Instant lastActivityAt, Instant endedAt) { }
    public PageResponse<Profile> profiles(String query, PageQuery page) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (term.length() > 255 || term.indexOf('\0') >= 0) throw new dot.kt.priceChecker.exceptions.common.ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PARAMETERS", "Search text must be at most 255 characters.");
        String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<TelegramUser> filter = (root, criteria, builder) -> {
            var text = builder.or(builder.like(builder.lower(root.get("realName")), pattern, '\\'),
                    builder.like(builder.lower(root.get("username")), pattern, '\\'), builder.like(builder.lower(root.get("firstName")), pattern, '\\'));
            if (term.matches("[0-9]{1,18}")) text = builder.or(text, builder.equal(root.get("telegramId"), Long.parseLong(term)));
            return term.isBlank() ? builder.conjunction() : text;
        };
        return PageResponse.from(users.findAll(filter, PageRequest.of(page.page(), page.size(), Sort.by(Sort.Direction.DESC, "updatedAt"))).map(this::profile));
    }
    public PageResponse<Conversation> history(long telegramId, PageQuery page) {
        return PageResponse.from(conversations.findAllByUserTelegramIdOrderByCreatedAtDesc(telegramId,
                PageRequest.of(page.page(), page.size())).map(c -> new Conversation(c.getId(), c.getPhase().name(),
                        c.getDomain() == null ? null : c.getDomain().name(), c.getGadgetType() == null ? null : c.getGadgetType().name(),
                        c.getQuestion(), snapshot(c.getSelectionJson()), c.getCreatedAt(), c.getLastActivityAt(), c.getEndedAt())));
    }
    private Profile profile(TelegramUser user) {
        return new Profile(user.getTelegramId(), user.getUsername(), user.getFirstName(), user.getLastName(), user.getRealName(),
                user.getSelectedItem() == null ? null : user.getSelectedItem().getId(),
                user.getSelectedListing() == null ? null : user.getSelectedListing().getId(), snapshot(user.getSelectionJson()), user.getUpdatedAt());
    }
    private BotOffer snapshot(String value) { return value == null ? null : json.readValue(value, BotOffer.class); }
}
