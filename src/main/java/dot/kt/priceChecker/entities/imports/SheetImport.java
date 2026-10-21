package dot.kt.priceChecker.entities.imports;

import dot.kt.priceChecker.entities.sellers.Seller;
import dot.kt.priceChecker.enums.imports.ImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sheet_imports")
public class SheetImport {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, updatable = false)
    private Seller seller;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "sheet_title", nullable = false, length = 255)
    private String sheetTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "items_json", nullable = false, columnDefinition = "text")
    private String itemsJson;

    @Column(name = "errors_json", nullable = false, columnDefinition = "text")
    private String errorsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected SheetImport() {
    }

    public SheetImport(Seller seller, String sourceUrl, String sheetTitle, int totalRows,
                       int validRows, int errorRows, String itemsJson, String errorsJson) {
        this.id = UUID.randomUUID();
        this.seller = seller;
        this.sourceUrl = sourceUrl;
        this.sheetTitle = sheetTitle;
        this.status = errorRows > 0 ? ImportStatus.INVALID : ImportStatus.PREVIEW;
        this.totalRows = totalRows;
        this.validRows = validRows;
        this.errorRows = errorRows;
        this.itemsJson = itemsJson;
        this.errorsJson = errorsJson;
        this.createdAt = Instant.now();
    }

    public void complete() {
        this.status = ImportStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void invalidate() {
        if (status == ImportStatus.COMPLETED) {
            throw new IllegalStateException("A completed import cannot be invalidated");
        }
        this.status = ImportStatus.INVALID;
    }

    public UUID getId() {
        return id;
    }

    public Seller getSeller() {
        return seller;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getSheetTitle() {
        return sheetTitle;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public String getErrorsJson() {
        return errorsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
