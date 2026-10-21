package dot.kt.priceChecker.service.sellers;

import dot.kt.priceChecker.dto.common.PageQuery;
import dot.kt.priceChecker.dto.common.PageResponse;
import dot.kt.priceChecker.dto.sellers.SellerResponse;
import dot.kt.priceChecker.dto.sellers.UpdateSellerRequest;
import dot.kt.priceChecker.entities.sellers.Seller;
import dot.kt.priceChecker.entities.users.AppUser;
import dot.kt.priceChecker.exceptions.common.ApiException;
import dot.kt.priceChecker.repo.sellers.SellerRepository;
import dot.kt.priceChecker.repo.users.UserRepository;
import dot.kt.priceChecker.service.security.CurrentUserService;
import dot.kt.priceChecker.utils.common.CurrencyCode;
import dot.kt.priceChecker.utils.sheets.GoogleSheetUrl;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerService {
    private final SellerRepository sellers;
    private final UserRepository users;
    private final CurrentUserService currentUser;

    public SellerService(SellerRepository sellers, UserRepository users, CurrentUserService currentUser) {
        this.sellers = sellers;
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('seller:read')")
    public SellerResponse current() { return SellerResponse.from(requireSeller(currentUser.id())); }

    @Transactional(readOnly = true)
    @PreAuthorize("@sellerAccess.canRead(#sellerId)")
    public Seller requireSeller(UUID sellerId) {
        return sellers.findById(sellerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "Seller account not found."));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@sellerAccess.canRead(#sellerId)")
    public SellerResponse get(UUID sellerId) { return SellerResponse.from(requireSeller(sellerId)); }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('project:read')")
    public PageResponse<SellerResponse> list(PageQuery query) {
        return PageResponse.from(sellers.findAll(PageRequest.of(query.page(), query.size(), Sort.by("id")))
                .map(SellerResponse::from));
    }

    @Transactional
    @PreAuthorize("hasAuthority('seller:write')")
    public SellerResponse update(UpdateSellerRequest request) { return update(currentUser.id(), request); }

    @Transactional
    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public SellerResponse update(UUID sellerId, UpdateSellerRequest request) {
        Seller seller = forUpdate(sellerId);
        String email = AppUser.normalizeEmail(request.email());
        if (!seller.getEmail().equals(email) && users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_IN_USE", "An account with this email already exists.");
        }
        seller.updateProfile(email, request.displayName(), CurrencyCode.validate(request.defaultCurrency()));
        return SellerResponse.from(sellers.saveAndFlush(seller));
    }

    @Transactional
    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public SellerResponse persistSheetLink(UUID sellerId, String url) {
        GoogleSheetUrl.parse(url);
        Seller seller = forUpdate(sellerId);
        seller.setGoogleSheetUrl(url.strip());
        return SellerResponse.from(sellers.saveAndFlush(seller));
    }

    @Transactional
    @PreAuthorize("hasAuthority('seller:write')")
    public void removeSheet() { removeSheet(currentUser.id()); }

    @Transactional
    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public void removeSheet(UUID sellerId) { forUpdate(sellerId).setGoogleSheetUrl(null); }

    @Transactional
    @PreAuthorize("hasAuthority('seller:write')")
    public void delete() { delete(currentUser.id()); }

    @Transactional
    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public void delete(UUID sellerId) {
        sellers.delete(forUpdate(sellerId));
        sellers.flush();
    }

    private Seller forUpdate(UUID sellerId) {
        return sellers.findByIdForUpdate(sellerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "Seller account not found."));
    }
}
