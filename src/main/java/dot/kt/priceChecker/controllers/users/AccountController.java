package dot.kt.priceChecker.controllers.users;

import dot.kt.priceChecker.dto.users.AccountResponse;
import dot.kt.priceChecker.service.security.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "The authenticated user's shared identity")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/me")
    @Operation(summary = "Get your account and current permissions")
    public AccountResponse current() { return accounts.current(); }
}
