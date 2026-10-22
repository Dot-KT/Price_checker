CREATE TABLE items (
    id UUID PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255) NOT NULL,
    brand_key VARCHAR(255) NOT NULL,
    model VARCHAR(255),
    identity_key VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_item_category CHECK (category IN ('PHONE', 'ELECTRICAL_APPLIANCE')),
    CONSTRAINT ck_item_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_item_brand CHECK (CHAR_LENGTH(TRIM(brand)) > 0)
);

CREATE TABLE phone_items (
    id UUID PRIMARY KEY REFERENCES items(id) ON DELETE CASCADE,
    series VARCHAR(255) NOT NULL,
    CONSTRAINT ck_phone_series CHECK (CHAR_LENGTH(TRIM(series)) > 0)
);

CREATE TABLE electrical_appliance_items (
    id UUID PRIMARY KEY REFERENCES items(id) ON DELETE CASCADE,
    appliance_type VARCHAR(255) NOT NULL,
    appliance_size VARCHAR(255),
    features VARCHAR(1000),
    CONSTRAINT ck_appliance_type CHECK (CHAR_LENGTH(TRIM(appliance_type)) > 0)
);

ALTER TABLE inventory_items ADD COLUMN item_id UUID REFERENCES items(id);
CREATE INDEX idx_item_category_brand ON items(category, brand_key);
CREATE INDEX idx_inventory_catalog ON inventory_items(item_id, active, currency, price_minor);
