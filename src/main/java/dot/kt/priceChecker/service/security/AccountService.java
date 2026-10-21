package dot.kt.priceChecker.service.security;

import dot.kt.priceChecker.dto.common.PageQuery;
import dot.kt.priceChecker.dto.common.PageResponse;
import dot.kt.priceChecker.dto.users.AccountResponse;
import dot.kt.priceChecker.repo.users.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final CurrentUserService currentUser;

    public AccountService(UserRepository users, CurrentUserService currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('account:read')")
    public AccountResponse current() {
        return users.findById(currentUser.id()).map(AccountResponse::from)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authentication is required."));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('project:read')")
    public PageResponse<AccountResponse> list(PageQuery query) {
        return PageResponse.from(users.findAll(PageRequest.of(query.page(), query.size(), Sort.by("id")))
                .map(AccountResponse::from));
    }
}
