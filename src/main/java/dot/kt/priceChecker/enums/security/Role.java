package dot.kt.priceChecker.enums.security;

import java.util.Set;

public enum Role {
    ADMIN(Set.of("account:read", "account:write", "seller:read", "seller:write", "project:read", "project:write")),
    MANAGER(Set.of("account:read", "project:read")),
    SELLER(Set.of("account:read", "account:write", "seller:read", "seller:write")),
    USER(Set.of("account:read", "account:write"));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> permissions() {
        return permissions;
    }
}
