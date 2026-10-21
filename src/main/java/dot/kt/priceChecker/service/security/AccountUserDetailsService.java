package dot.kt.priceChecker.service.security;

import dot.kt.priceChecker.config.security.AuthenticatedUser;
import dot.kt.priceChecker.entities.users.AppUser;
import dot.kt.priceChecker.repo.users.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public AccountUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String email) {
        return users.findByEmail(AppUser.normalizeEmail(email)).map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
    }
}
