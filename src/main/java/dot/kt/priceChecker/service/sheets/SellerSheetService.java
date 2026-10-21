package dot.kt.priceChecker.service.sheets;

import dot.kt.priceChecker.dto.sellers.SellerResponse;
import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import dot.kt.priceChecker.exceptions.common.ApiException;
import dot.kt.priceChecker.service.security.CurrentUserService;
import dot.kt.priceChecker.service.sellers.SellerService;
import dot.kt.priceChecker.utils.sheets.GoogleSheetUrl;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class SellerSheetService {
    private final SellerService sellers;
    private final CurrentUserService currentUser;
    private final SheetReader reader;

    public SellerSheetService(SellerService sellers, CurrentUserService currentUser, SheetReader reader) {
        this.sellers = sellers;
        this.currentUser = currentUser;
        this.reader = reader;
    }

    @PreAuthorize("hasAuthority('seller:write')")
    public SellerResponse save(String url) { return save(currentUser.id(), url); }

    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public SellerResponse save(UUID sellerId, String url) {
        GoogleSheetUrl.parse(url);
        // Verify public access before starting the transaction that saves the link.
        reader.read(url);
        return sellers.persistSheetLink(sellerId, url);
    }

    @PreAuthorize("hasAuthority('seller:read')")
    public SheetSnapshot read() { return read(currentUser.id()); }

    @PreAuthorize("@sellerAccess.canRead(#sellerId)")
    public SheetSnapshot read(UUID sellerId) { return reader.read(savedLink(sellerId)); }

    @PreAuthorize("@sellerAccess.canRead(#sellerId)")
    public String savedLink(UUID sellerId) {
        String url = sellers.get(sellerId).googleSheetUrl();
        if (url == null || url.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SHEET_NOT_SAVED", "Save a public Google Sheets link first.");
        }
        return url;
    }
}
