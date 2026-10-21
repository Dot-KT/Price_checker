package dot.kt.priceChecker.service.security;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service("sellerAccess")
public class SellerAccessService {
    private final CurrentUserService currentUser;

    public SellerAccessService(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    public boolean canRead(UUID sellerId) {
        return currentUser.hasAuthority("project:read")
                || (currentUser.hasAuthority("seller:read") && currentUser.id().equals(sellerId));
    }

    public boolean canWrite(UUID sellerId) {
        return currentUser.hasAuthority("project:write")
                || (currentUser.hasAuthority("seller:write") && currentUser.id().equals(sellerId));
    }
}
