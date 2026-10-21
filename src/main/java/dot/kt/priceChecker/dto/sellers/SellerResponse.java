package dot.kt.priceChecker.dto.sellers;

import dot.kt.priceChecker.entities.sellers.Seller;
import java.util.UUID;

public record SellerResponse(UUID id, String email, String displayName, String defaultCurrency, String googleSheetUrl) {
    public static SellerResponse from(Seller seller) {
        return new SellerResponse(seller.getId(), seller.getEmail(), seller.getDisplayName(),
                seller.getDefaultCurrency(), seller.getGoogleSheetUrl());
    }
}
