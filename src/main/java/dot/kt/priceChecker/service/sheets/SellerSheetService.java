package dot.kt.priceChecker.service.sheets;

import dot.kt.priceChecker.dto.sellers.SellerResponse;
import dot.kt.priceChecker.dto.sheets.SheetSnapshot;
import dot.kt.priceChecker.enums.imports.ImportStatus;
import dot.kt.priceChecker.exceptions.sheets.SheetValidationException;
import dot.kt.priceChecker.service.imports.SheetImportService;
import dot.kt.priceChecker.service.security.CurrentUserService;
import dot.kt.priceChecker.service.sellers.SellerService;
import dot.kt.priceChecker.utils.sheets.GoogleSheetUrl;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class SellerSheetService {
    private final SellerService sellers;
    private final CurrentUserService currentUser;
    private final SheetReader reader;
    private final SheetImportService imports;

    public SellerSheetService(SellerService sellers, CurrentUserService currentUser, SheetReader reader,
            SheetImportService imports) {
        this.sellers = sellers;
        this.currentUser = currentUser;
        this.reader = reader;
        this.imports = imports;
    }

    @PreAuthorize("hasAuthority('seller:write')")
    public SellerResponse save(String url) { return save(currentUser.id(), url); }

    @PreAuthorize("@sellerAccess.canWrite(#sellerId)")
    public SellerResponse save(UUID sellerId, String url) {
        GoogleSheetUrl.parse(url);
        // Download and validate before acquiring the lock used to apply inventory and save the link.
        var preview = imports.preview(sellerId, url);
        if (preview.status() != ImportStatus.PREVIEW) {
            throw new SheetValidationException(preview.id(), preview.errors());
        }
        return imports.confirmAndSaveSheet(sellerId, preview.id(), url);
    }

    @PreAuthorize("hasAuthority('seller:read')")
    public SheetSnapshot read() { return read(currentUser.id()); }

    @PreAuthorize("@sellerAccess.canRead(#sellerId)")
    public SheetSnapshot read(UUID sellerId) { return reader.read(savedLink(sellerId)); }

    @PreAuthorize("@sellerAccess.canRead(#sellerId)")
    public String savedLink(UUID sellerId) {
        return sellers.savedSheetLink(sellerId);
    }
}
