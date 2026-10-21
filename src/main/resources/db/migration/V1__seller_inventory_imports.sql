CREATE TABLE sellers (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    default_currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_sellers_email UNIQUE (email),
    CONSTRAINT ck_sellers_email_lowercase CHECK (email = LOWER(email)),
    CONSTRAINT ck_sellers_currency CHECK (CHAR_LENGTH(default_currency) = 3 AND default_currency = UPPER(default_currency))
);

CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL,
    seller_sku VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    model VARCHAR(255),
    description VARCHAR(4000),
    price_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    quantity INTEGER,
    source_row INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_inventory_seller FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT uk_inventory_seller_sku UNIQUE (seller_id, seller_sku),
    CONSTRAINT ck_inventory_sku_nonblank CHECK (CHAR_LENGTH(TRIM(seller_sku)) > 0),
    CONSTRAINT ck_inventory_name_nonblank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_inventory_price_nonnegative CHECK (price_minor >= 0),
    CONSTRAINT ck_inventory_quantity_nonnegative CHECK (quantity IS NULL OR quantity >= 0),
    CONSTRAINT ck_inventory_source_row CHECK (source_row > 0),
    CONSTRAINT ck_inventory_currency CHECK (CHAR_LENGTH(currency) = 3 AND currency = UPPER(currency))
);

CREATE INDEX idx_inventory_seller_active ON inventory_items (seller_id, active);

CREATE TABLE sheet_imports (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    sheet_title VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_rows INTEGER NOT NULL,
    valid_rows INTEGER NOT NULL,
    error_rows INTEGER NOT NULL,
    items_json TEXT NOT NULL,
    errors_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_sheet_import_seller FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT ck_sheet_import_status CHECK (status IN ('PREVIEW', 'INVALID', 'COMPLETED')),
    CONSTRAINT ck_sheet_import_counts CHECK (total_rows >= 0 AND valid_rows >= 0 AND error_rows >= 0 AND total_rows = valid_rows + error_rows),
    CONSTRAINT ck_sheet_import_completion CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND completed_at IS NULL))
);

CREATE INDEX idx_sheet_imports_seller_created ON sheet_imports (seller_id, created_at);
CREATE INDEX idx_sheet_imports_seller_status_completed ON sheet_imports (seller_id, status, completed_at);
